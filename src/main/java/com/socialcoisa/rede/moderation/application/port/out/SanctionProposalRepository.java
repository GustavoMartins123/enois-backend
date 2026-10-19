package com.socialcoisa.rede.moderation.application.port.out;

import java.util.List;
import java.util.Optional;

import com.socialcoisa.rede.moderation.domain.SanctionProposal;
import com.socialcoisa.rede.moderation.domain.SanctionProposalStatus;
import com.socialcoisa.rede.moderation.domain.SanctionProposalVote;

public interface SanctionProposalRepository {

	SanctionProposal save(SanctionProposal proposal);

	Optional<SanctionProposal> findById(String id);

	/** Evita duas propostas abertas contra a mesma pessoa. */
	Optional<SanctionProposal> findPendingByTarget(String targetUserId);

	List<SanctionProposal> findByStatus(SanctionProposalStatus status, int limit);

	SanctionProposalVote saveVote(SanctionProposalVote vote);

	Optional<SanctionProposalVote> findVote(String proposalId, String moderatorId);

	List<SanctionProposalVote> findVotes(String proposalId);
}
