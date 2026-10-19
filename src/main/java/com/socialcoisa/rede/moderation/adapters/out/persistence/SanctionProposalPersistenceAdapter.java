package com.socialcoisa.rede.moderation.adapters.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import com.socialcoisa.rede.moderation.application.port.out.SanctionProposalRepository;
import com.socialcoisa.rede.moderation.domain.SanctionProposal;
import com.socialcoisa.rede.moderation.domain.SanctionProposalStatus;
import com.socialcoisa.rede.moderation.domain.SanctionProposalVote;

@Repository
class SanctionProposalPersistenceAdapter implements SanctionProposalRepository {

	private final SpringDataSanctionProposalJpaRepository proposals;
	private final SpringDataSanctionProposalVoteJpaRepository votes;

	SanctionProposalPersistenceAdapter(SpringDataSanctionProposalJpaRepository proposals,
			SpringDataSanctionProposalVoteJpaRepository votes) {
		this.proposals = proposals;
		this.votes = votes;
	}

	@Override
	public SanctionProposal save(SanctionProposal proposal) {
		return proposals.save(SanctionProposalJpaEntity.fromDomain(proposal)).toDomain();
	}

	@Override
	public Optional<SanctionProposal> findById(String id) {
		return proposals.findById(id).map(SanctionProposalJpaEntity::toDomain);
	}

	@Override
	public Optional<SanctionProposal> findPendingByTarget(String targetUserId) {
		return proposals.findFirstByTargetUserIdAndStatus(targetUserId, SanctionProposalStatus.PENDING)
			.map(SanctionProposalJpaEntity::toDomain);
	}

	@Override
	public List<SanctionProposal> findByStatus(SanctionProposalStatus status, int limit) {
		return proposals.findByStatusOrderByCreatedAtAsc(status, PageRequest.of(0, Math.max(1, limit)))
			.stream()
			.map(SanctionProposalJpaEntity::toDomain)
			.toList();
	}

	@Override
	public SanctionProposalVote saveVote(SanctionProposalVote vote) {
		return votes.save(SanctionProposalVoteJpaEntity.fromDomain(vote)).toDomain();
	}

	@Override
	public Optional<SanctionProposalVote> findVote(String proposalId, String moderatorId) {
		return votes.findByProposalIdAndModeratorId(proposalId, moderatorId)
			.map(SanctionProposalVoteJpaEntity::toDomain);
	}

	@Override
	public List<SanctionProposalVote> findVotes(String proposalId) {
		return votes.findByProposalIdOrderByCreatedAtAsc(proposalId).stream()
			.map(SanctionProposalVoteJpaEntity::toDomain)
			.toList();
	}
}
