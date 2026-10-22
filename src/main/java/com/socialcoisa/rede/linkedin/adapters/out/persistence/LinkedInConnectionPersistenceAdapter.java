package com.socialcoisa.rede.linkedin.adapters.out.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.socialcoisa.rede.linkedin.application.port.out.LinkedInConnectionRepository;
import com.socialcoisa.rede.linkedin.domain.LinkedInConnection;

@Repository
class LinkedInConnectionPersistenceAdapter implements LinkedInConnectionRepository {

	private final SpringDataLinkedInConnectionJpaRepository repository;

	LinkedInConnectionPersistenceAdapter(SpringDataLinkedInConnectionJpaRepository repository) {
		this.repository = repository;
	}

	@Override
	public Optional<LinkedInConnection> findByUserId(String userId) {
		return repository.findById(userId).map(LinkedInConnectionJpaEntity::toDomain);
	}

	@Override
	public void save(LinkedInConnection connection) {
		// O id e atribuido (user_id): o merge do JPA insere quando nao existe e atualiza quando existe.
		repository.save(LinkedInConnectionJpaEntity.fromDomain(connection));
	}

	@Override
	public void deleteByUserId(String userId) {
		repository.deleteById(userId);
	}
}
