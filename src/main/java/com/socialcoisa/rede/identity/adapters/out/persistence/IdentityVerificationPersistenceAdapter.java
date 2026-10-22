package com.socialcoisa.rede.identity.adapters.out.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.socialcoisa.rede.identity.application.port.out.IdentityVerificationRepository;
import com.socialcoisa.rede.identity.domain.IdentityVerification;

@Repository
class IdentityVerificationPersistenceAdapter implements IdentityVerificationRepository {

	private final SpringDataIdentityVerificationJpaRepository repository;

	IdentityVerificationPersistenceAdapter(SpringDataIdentityVerificationJpaRepository repository) {
		this.repository = repository;
	}

	@Override
	public Optional<IdentityVerification> findByUserId(String userId) {
		return repository.findByUserId(userId).map(IdentityVerificationJpaEntity::toDomain);
	}

	@Override
	public Optional<IdentityVerification> findByAttemptId(String attemptId) {
		return repository.findByAttemptId(attemptId).map(IdentityVerificationJpaEntity::toDomain);
	}

	@Override
	public void save(IdentityVerification verification) {
		repository.save(IdentityVerificationJpaEntity.fromDomain(verification));
	}
}
