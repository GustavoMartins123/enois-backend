package com.socialcoisa.rede.companies.adapters.out.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataMicroProjectProposalJpaRepository extends JpaRepository<MicroProjectProposalJpaEntity, String> {

	boolean existsByProjectIdAndUserId(String projectId, String userId);

	List<MicroProjectProposalJpaEntity> findByProjectIdOrderByProposedAtAsc(String projectId);

	List<MicroProjectProposalJpaEntity> findTop20ByUserIdOrderByProposedAtDesc(String userId);
}
