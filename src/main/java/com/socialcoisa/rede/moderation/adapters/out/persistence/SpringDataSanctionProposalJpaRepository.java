package com.socialcoisa.rede.moderation.adapters.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.socialcoisa.rede.moderation.domain.SanctionProposalStatus;

interface SpringDataSanctionProposalJpaRepository
		extends JpaRepository<SanctionProposalJpaEntity, String> {

	Optional<SanctionProposalJpaEntity> findFirstByTargetUserIdAndStatus(String targetUserId,
			SanctionProposalStatus status);

	List<SanctionProposalJpaEntity> findByStatusOrderByCreatedAtAsc(SanctionProposalStatus status,
			Pageable pageable);
}
