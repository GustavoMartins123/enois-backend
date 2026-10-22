package com.socialcoisa.rede.push.application.port.out;

import java.util.List;
import java.util.Optional;

import com.socialcoisa.rede.push.domain.PushSubscription;

public interface PushSubscriptionRepository {

	PushSubscription save(PushSubscription subscription);

	Optional<PushSubscription> findByEndpoint(String endpoint);

	List<PushSubscription> findByUserId(String userId);

	void deleteById(String id);

	void deleteByEndpointAndUserId(String endpoint, String userId);
}