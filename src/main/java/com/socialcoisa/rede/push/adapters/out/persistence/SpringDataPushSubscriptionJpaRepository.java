package com.socialcoisa.rede.push.adapters.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataPushSubscriptionJpaRepository extends JpaRepository<PushSubscriptionJpaEntity, String> {

	Optional<PushSubscriptionJpaEntity> findByEndpoint(String endpoint);

	List<PushSubscriptionJpaEntity> findByUserId(String userId);

	void deleteByEndpointAndUserId(String endpoint, String userId);
}
