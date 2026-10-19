package com.socialcoisa.rede.moderation.adapters.out.persistence;

import java.time.Instant;

import com.socialcoisa.rede.moderation.domain.SanctionProposalVote;
import com.socialcoisa.rede.moderation.domain.SanctionVoteType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "moderator_sanction_votes", indexes = {
	@Index(name = "idx_sanction_vote_proposal", columnList = "proposal_id") })
class SanctionProposalVoteJpaEntity {

	@Id
	private String id;

	@Column(name = "proposal_id", nullable = false)
	private String proposalId;

	@Column(name = "moderator_id", nullable = false)
	private String moderatorId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SanctionVoteType vote;

	@Column(length = 1000)
	private String note;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected SanctionProposalVoteJpaEntity() {
	}

	static SanctionProposalVoteJpaEntity fromDomain(SanctionProposalVote vote) {
		SanctionProposalVoteJpaEntity e = new SanctionProposalVoteJpaEntity();
		e.id = vote.id();
		e.proposalId = vote.proposalId();
		e.moderatorId = vote.moderatorId();
		e.vote = vote.vote();
		e.note = vote.note();
		e.createdAt = vote.createdAt();
		return e;
	}

	SanctionProposalVote toDomain() {
		return new SanctionProposalVote(id, proposalId, moderatorId, vote, note, createdAt);
	}
}
