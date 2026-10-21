package com.socialcoisa.rede.identity.adapters.out.persistence;

import java.time.Instant;
import java.util.Optional;

import org.slf4j.MDC;
import org.springframework.stereotype.Repository;

import com.socialcoisa.rede.identity.application.port.out.AuthSessionRepository;
import com.socialcoisa.rede.shared.web.RequestObservabilityFilter;

@Repository
class AuthSessionPersistenceAdapter implements AuthSessionRepository {

	private final SpringDataAuthSessionJpaRepository repository;

	AuthSessionPersistenceAdapter(SpringDataAuthSessionJpaRepository repository) {
		this.repository = repository;
	}

	@Override
	public void save(String token, String refreshToken, String userId, Instant createdAt, Instant expiresAt,
			Instant refreshExpiresAt, String ip) {
		// ponytail: callers other than password login (e-mail verify, 2FA, OAuth, refresh) pass null;
		// the request IP from the MDC fills it in one place instead of threading ip through every use case.
		String sessionIp = ip != null ? ip : MDC.get(RequestObservabilityFilter.MDC_CLIENT_IP);
		repository.save(new AuthSessionJpaEntity(token, refreshToken, userId, createdAt, expiresAt, refreshExpiresAt,
			sessionIp));
	}

	@Override
	public Optional<String> findActiveUserId(String token, Instant now) {
		return repository.findById(token)
			.filter(session -> session.activeAt(now))
			.map(AuthSessionJpaEntity::userId);
	}

	@Override
	public Optional<String> findActiveUserIdByRefreshToken(String refreshToken, Instant now) {
		return repository.findByRefreshToken(refreshToken)
			.filter(session -> session.activeRefreshAt(now))
			.map(AuthSessionJpaEntity::userId);
	}

	@Override
	public void revoke(String token, Instant revokedAt) {
		repository.findById(token).ifPresent(session -> {
			session.revoke(revokedAt);
			repository.save(session);
		});
	}

	@Override
	public void revokeByRefreshToken(String refreshToken, Instant revokedAt) {
		repository.findByRefreshToken(refreshToken).ifPresent(session -> {
			session.revoke(revokedAt);
			repository.save(session);
		});
	}

	@Override
	public void revokeAllByUser(String userId, Instant revokedAt) {
		repository.revokeAllByUser(userId, revokedAt);
	}

	@Override
	public java.util.List<String> findRecentIpsByUser(String userId, int limit) {
		return repository.findRecentIpsByUser(userId, org.springframework.data.domain.PageRequest.of(0,
			Math.max(1, limit)));
	}
}
