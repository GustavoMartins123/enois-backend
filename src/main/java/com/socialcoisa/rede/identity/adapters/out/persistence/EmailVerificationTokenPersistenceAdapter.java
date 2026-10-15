package com.socialcoisa.rede.identity.adapters.out.persistence;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.socialcoisa.rede.identity.application.port.out.EmailVerificationTokenRepository;
import com.socialcoisa.rede.identity.domain.EmailVerificationToken;

@Repository
class EmailVerificationTokenPersistenceAdapter implements EmailVerificationTokenRepository {

	private final SpringDataEmailVerificationTokenJpaRepository repository;
	private final Clock clock;

	EmailVerificationTokenPersistenceAdapter(SpringDataEmailVerificationTokenJpaRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	@Override
	public void save(EmailVerificationToken token) {
		repository.save(EmailVerificationTokenJpaEntity.fromDomain(token));
	}

	@Override
	public Optional<EmailVerificationToken> findByToken(String token) {
		return repository.findById(token).map(EmailVerificationTokenJpaEntity::toDomain);
	}

	@Override
	public void markUsed(String token) {
		repository.findById(token).ifPresent(entity -> {
			entity.markUsed(clock.instant());
			repository.save(entity);
		});
	}

	@Override
	public int markAllUsedByUserId(String userId, Instant now) {
		return repository.markAllUsedByUserId(userId, now);
	}

	@Override
	public int countByUserIdSince(String userId, Instant since) {
		return repository.countByUserIdAndCreatedAtAfter(userId, since);
	}
}