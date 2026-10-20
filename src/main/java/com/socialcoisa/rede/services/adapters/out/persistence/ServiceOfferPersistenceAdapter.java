package com.socialcoisa.rede.services.adapters.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.socialcoisa.rede.services.application.port.out.ServiceOfferRepository;
import com.socialcoisa.rede.services.domain.ServiceOffer;
import com.socialcoisa.rede.services.domain.ServiceStatus;

@Repository
public class ServiceOfferPersistenceAdapter implements ServiceOfferRepository {

	private final SpringDataServiceOfferJpaRepository repository;

	ServiceOfferPersistenceAdapter(SpringDataServiceOfferJpaRepository repository) {
		this.repository = repository;
	}

	@Override
	public ServiceOffer save(ServiceOffer offer) {
		// O id é atribuído: o merge do JPA insere quando não existe e atualiza quando existe.
		return repository.save(ServiceOfferJpaEntity.fromDomain(offer)).toDomain();
	}

	@Override
	public void delete(ServiceOffer offer) {
		repository.deleteById(offer.id());
	}

	@Override
	public Optional<ServiceOffer> findById(String id) {
		return repository.findById(id).map(ServiceOfferJpaEntity::toDomain);
	}

	@Override
	public Optional<ServiceOffer> findByUserIdAndSlug(String userId, String slug) {
		return repository.findByUserIdAndSlug(userId, slug).map(ServiceOfferJpaEntity::toDomain);
	}

	@Override
	public List<ServiceOffer> findByUserOrderByUpdatedAtDesc(String userId) {
		return repository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
			.map(ServiceOfferJpaEntity::toDomain)
			.toList();
	}

	@Override
	public List<ServiceOffer> findByStatusOrderByCreatedAtDesc(ServiceStatus status) {
		return repository.findByStatusOrderByCreatedAtDesc(status.name()).stream()
			.map(ServiceOfferJpaEntity::toDomain)
			.toList();
	}
}
