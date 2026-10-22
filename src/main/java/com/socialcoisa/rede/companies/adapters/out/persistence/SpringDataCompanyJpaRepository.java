package com.socialcoisa.rede.companies.adapters.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataCompanyJpaRepository extends JpaRepository<CompanyJpaEntity, String> {

	Optional<CompanyJpaEntity> findBySlug(String slug);

	boolean existsBySlug(String slug);

	List<CompanyJpaEntity> findByOwnerUserIdOrderByCreatedAtDesc(String ownerUserId, Pageable pageable);

	List<CompanyJpaEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
