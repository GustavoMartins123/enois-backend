package com.socialcoisa.rede.identity.adapters.out.persistence;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.socialcoisa.rede.identity.application.port.out.PendingSignupRepository;
import com.socialcoisa.rede.identity.domain.PendingSignup;

@Repository
class PendingSignupPersistenceAdapter implements PendingSignupRepository {

	private final SpringDataPendingSignupJpaRepository repository;

	PendingSignupPersistenceAdapter(SpringDataPendingSignupJpaRepository repository) {
		this.repository = repository;
	}

	@Override
	public void save(PendingSignup signup) {
		repository.save(PendingSignupJpaEntity.fromDomain(signup));
	}

	@Override
	public Optional<PendingSignup> findByTokenHash(String tokenHash) {
		return repository.findByTokenHash(tokenHash).map(PendingSignupJpaEntity::toDomain);
	}

	@Override
	public Optional<PendingSignup> findByEmail(String email) {
		return repository.findByEmail(email).map(PendingSignupJpaEntity::toDomain);
	}

	@Override
	public void deleteByEmail(String email) {
		repository.findByEmail(email).ifPresent(repository::delete);
	}

	@Override
	public void deleteExpired(Instant now) {
		repository.deleteExpiredBefore(now);
	}
}
