package com.socialcoisa.rede.jobs.adapters.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataExternalJobJpaRepository extends JpaRepository<ExternalJobJpaEntity, String> {

	Optional<ExternalJobJpaEntity> findByPlatformAndExternalId(String platform, String externalId);

	Optional<ExternalJobJpaEntity> findFirstByCanonicalUrl(String canonicalUrl);

	List<ExternalJobJpaEntity> findAllByOrderByLastCheckedAtDesc(Pageable pageable);

	List<ExternalJobJpaEntity> findByTitleContainingIgnoreCaseOrderByLastCheckedAtDesc(String query, Pageable pageable);
}
