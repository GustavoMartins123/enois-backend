package com.socialcoisa.rede.support.adapters.out.persistence;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import com.socialcoisa.rede.support.application.port.out.SupportRequestRepository;
import com.socialcoisa.rede.support.domain.SupportRequest;
import com.socialcoisa.rede.support.domain.SupportStatus;

@Repository
class SupportRequestPersistenceAdapter implements SupportRequestRepository {

	private final SpringDataSupportRequestJpaRepository repository;

	SupportRequestPersistenceAdapter(SpringDataSupportRequestJpaRepository repository) {
		this.repository = repository;
	}

	@Override
	public SupportRequest save(SupportRequest request) {
		return repository.save(SupportRequestJpaEntity.fromDomain(request)).toDomain();
	}

	@Override
	public List<SupportRequest> findByStatus(SupportStatus status, int limit) {
		return repository.findByStatusOrderByCreatedAtDesc(status, PageRequest.of(0, limit)).stream()
			.map(SupportRequestJpaEntity::toDomain)
			.toList();
	}

	@Override
	public long countByUserSince(String userId, Instant since) {
		return repository.countByUserIdAndCreatedAtAfter(userId, since);
	}
}
