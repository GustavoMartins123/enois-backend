package com.socialcoisa.rede.moderation.adapters.out.persistence;

import java.time.Instant;

import com.socialcoisa.rede.moderation.domain.ModerationVote;
import com.socialcoisa.rede.moderation.domain.ModerationVoteType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "moderation_votes",
	uniqueConstraints = @UniqueConstraint(name = "uk_mod_vote", columnNames = { "report_id", "moderator_id" }),
	indexes = @Index(name = "idx_mod_vote_report", columnList = "report_id"))
class ModerationVoteJpaEntity {

	@Id
	private String id;

	@Column(name = "report_id", nullable = false)
	private String reportId;

	@Column(name = "moderator_id", nullable = false)
	private String moderatorId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ModerationVoteType vote;

	@Column(length = 1000)
	private String note;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected ModerationVoteJpaEntity() {
	}

	static ModerationVoteJpaEntity fromDomain(ModerationVote vote) {
		ModerationVoteJpaEntity e = new ModerationVoteJpaEntity();
		e.id = vote.id();
		e.reportId = vote.reportId();
		e.moderatorId = vote.moderatorId();
		e.vote = vote.vote();
		e.note = vote.note();
		e.createdAt = vote.createdAt();
		e.updatedAt = vote.updatedAt();
		return e;
	}

	ModerationVote toDomain() {
		return ModerationVote.restore(id, reportId, moderatorId, vote, note, createdAt, updatedAt);
	}
}
