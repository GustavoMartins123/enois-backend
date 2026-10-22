package com.socialcoisa.rede.push.adapters.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.socialcoisa.rede.push.application.port.out.PushSubscriptionRepository;
import com.socialcoisa.rede.push.domain.PushSubscription;

@Repository
class PushSubscriptionPersistenceAdapter implements PushSubscriptionRepository {

	private final SpringDataPushSubscriptionJpaRepository repository;

	PushSubscriptionPersistenceAdapter(SpringDataPushSubscriptionJpaRepository repository) {
		this.repository = repository;
	}

	@Override
	public PushSubscription save(PushSubscription subscription) {
		return repository.save(PushSubscriptionJpaEntity.fromDomain(subscription)).toDomain();
	}

	@Override
	public Optional<PushSubscription> findByEndpoint(String endpoint) {
		return repository.findByEndpoint(endpoint).map(PushSubscriptionJpaEntity::toDomain);
	}

	@Override
	public List<PushSubscription> findByUserId(String userId) {
		return repository.findByUserId(userId).stream().map(PushSubscriptionJpaEntity::toDomain).toList();
	}

	@Override
	public void deleteById(String id) {
		repository.deleteById(id);
	}

	@Override
	public void deleteByEndpointAndUserId(String endpoint, String userId) {
		repository.deleteByEndpointAndUserId(endpoint, userId);
	}
}
