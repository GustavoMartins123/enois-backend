package com.socialcoisa.rede.jobs.adapters.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import com.socialcoisa.rede.jobs.application.port.out.ExternalJobRepository;
import com.socialcoisa.rede.jobs.domain.ExternalJob;

@Repository
class ExternalJobPersistenceAdapter implements ExternalJobRepository {

	private final SpringDataExternalJobJpaRepository repository;

	ExternalJobPersistenceAdapter(SpringDataExternalJobJpaRepository repository) {
		this.repository = repository;
	}

	@Override
	public Optional<ExternalJob> findBySource(String platform, String externalId) {
		return repository.findByPlatformAndExternalId(platform, externalId).map(ExternalJobJpaEntity::toDomain);
	}

	@Override
	public void save(ExternalJob job) {
		repository.save(ExternalJobJpaEntity.fromDomain(job));
	}

	@Override
	@org.springframework.cache.annotation.Cacheable("externalJobs")
	public List<ExternalJob> search(String query, int limit) {
		return repository
			.findByTitleContainingIgnoreCaseOrderByLastCheckedAtDesc(query, PageRequest.of(0, Math.max(1, limit)))
			.stream().map(ExternalJobJpaEntity::toDomain).toList();
	}

	@Override
	@org.springframework.cache.annotation.Cacheable("externalJobs")
	public List<ExternalJob> findRecent(int limit) {
		return repository.findAllByOrderByLastCheckedAtDesc(PageRequest.of(0, Math.max(1, limit))).stream()
			.map(ExternalJobJpaEntity::toDomain)
			.toList();
	}
}
