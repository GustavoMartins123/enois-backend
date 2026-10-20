package com.socialcoisa.rede.services.application.port.out;

import java.util.List;
import java.util.Optional;

import com.socialcoisa.rede.services.domain.ServiceOffer;
import com.socialcoisa.rede.services.domain.ServiceStatus;

/** Persistência das ofertas de serviço. O par (user_id, slug) é único. */
public interface ServiceOfferRepository {

	ServiceOffer save(ServiceOffer offer);

	void delete(ServiceOffer offer);

	Optional<ServiceOffer> findById(String id);

	Optional<ServiceOffer> findByUserIdAndSlug(String userId, String slug);

	List<ServiceOffer> findByUserOrderByUpdatedAtDesc(String userId);

	List<ServiceOffer> findByStatusOrderByCreatedAtDesc(ServiceStatus status);
}
