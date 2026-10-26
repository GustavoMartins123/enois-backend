package com.socialcoisa.rede.contributors.adapters.out.persistence;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.socialcoisa.rede.contributors.domain.ContributorApplicationStatus;

interface SpringDataContributorApplicationJpaRepository
		extends JpaRepository<ContributorApplicationJpaEntity, String> {

	List<ContributorApplicationJpaEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

	List<ContributorApplicationJpaEntity> findByStatusOrderByCreatedAtDesc(ContributorApplicationStatus status,
			Pageable pageable);
}
