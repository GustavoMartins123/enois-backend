package com.socialcoisa.rede.moderation.adapters.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataSanctionProposalVoteJpaRepository
		extends JpaRepository<SanctionProposalVoteJpaEntity, String> {

	Optional<SanctionProposalVoteJpaEntity> findByProposalIdAndModeratorId(String proposalId,
			String moderatorId);

	List<SanctionProposalVoteJpaEntity> findByProposalIdOrderByCreatedAtAsc(String proposalId);
}
