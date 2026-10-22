package com.socialcoisa.rede.identity.adapters.out.persistence;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.socialcoisa.rede.identity.application.port.out.OAuthAuthorizationRequestRepository;
import com.socialcoisa.rede.identity.domain.OAuthAuthorizationRequest;

@Repository
class OAuthAuthorizationRequestPersistenceAdapter implements OAuthAuthorizationRequestRepository {

	private final SpringDataOAuthAuthorizationRequestJpaRepository repository;

	OAuthAuthorizationRequestPersistenceAdapter(SpringDataOAuthAuthorizationRequestJpaRepository repository) {
		this.repository = repository;
	}

	@Override
	public void save(OAuthAuthorizationRequest request) {
		repository.save(OAuthAuthorizationRequestJpaEntity.fromDomain(request));
	}

	@Override
	public Optional<OAuthAuthorizationRequest> findByState(String state) {
		return repository.findById(state).map(OAuthAuthorizationRequestJpaEntity::toDomain);
	}

	@Override
	public void deleteByState(String state) {
		repository.deleteById(state);
	}

	@Override
	public int deleteExpired(Instant now) {
		var expired = repository.findByExpiresAtBefore(now);
		repository.deleteAll(expired);
		return expired.size();
	}
}