package com.socialcoisa.rede.moderation.adapters.out.persistence;

import java.time.Instant;

import com.socialcoisa.rede.moderation.domain.SanctionProposal;
import com.socialcoisa.rede.moderation.domain.SanctionProposalStatus;
import com.socialcoisa.rede.moderation.domain.SanctionType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "moderator_sanction_proposals", indexes = {
	@Index(name = "idx_sanction_proposal_status", columnList = "status, created_at"),
	@Index(name = "idx_sanction_proposal_target", columnList = "target_user_id, status") })
class SanctionProposalJpaEntity {

	@Id
	private String id;

	@Column(name = "target_user_id", nullable = false)
	private String targetUserId;

	@Column(name = "target_username", nullable = false, length = 120)
	private String targetUsername;

	@Enumerated(EnumType.STRING)
	@Column(name = "sanction_type", nullable = false, length = 20)
	private SanctionType type;

	@Column(nullable = false, length = 2000)
	private String reason;

	@Column(name = "proposer_id", nullable = false)
	private String proposerId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SanctionProposalStatus status;

	@Column(name = "required_votes", nullable = false)
	private int requiredVotes;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "decided_at")
	private Instant decidedAt;

	protected SanctionProposalJpaEntity() {
	}

	static SanctionProposalJpaEntity fromDomain(SanctionProposal proposal) {
		SanctionProposalJpaEntity e = new SanctionProposalJpaEntity();
		e.id = proposal.id();
		e.targetUserId = proposal.targetUserId();
		e.targetUsername = proposal.targetUsername();
		e.type = proposal.type();
		e.reason = proposal.reason();
		e.proposerId = proposal.proposerId();
		e.status = proposal.status();
		e.requiredVotes = proposal.requiredVotes();
		e.createdAt = proposal.createdAt();
		e.decidedAt = proposal.decidedAt();
		return e;
	}

	SanctionProposal toDomain() {
		return new SanctionProposal(id, targetUserId, targetUsername, type, reason, proposerId, status,
			requiredVotes, createdAt, decidedAt);
	}
}
