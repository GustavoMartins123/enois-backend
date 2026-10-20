package com.socialcoisa.rede.services.adapters.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataServiceOfferJpaRepository extends JpaRepository<ServiceOfferJpaEntity, String> {

	Optional<ServiceOfferJpaEntity> findByUserIdAndSlug(String userId, String slug);

	List<ServiceOfferJpaEntity> findByUserIdOrderByUpdatedAtDesc(String userId);

	List<ServiceOfferJpaEntity> findByStatusOrderByCreatedAtDesc(String status);

	/** Faxina do worker (ainda sem uso, mas o modelo já prevê expiração de serviços antigos). */
	List<ServiceOfferJpaEntity> findByUpdatedAtBefore(Instant cutoff);
}
