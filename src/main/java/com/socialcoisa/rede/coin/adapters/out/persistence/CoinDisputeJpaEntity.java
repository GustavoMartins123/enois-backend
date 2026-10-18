package com.socialcoisa.rede.coin.adapters.out.persistence;

import java.time.Instant;

import com.socialcoisa.rede.coin.domain.CoinDispute;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "coin_disputes")
class CoinDisputeJpaEntity {

	@Id
	private String id;

	@Column(name = "entry_ref", nullable = false)
	private String entryRef;

	@Column(name = "sender_id", nullable = false)
	private String senderId;

	@Column(name = "receiver_id", nullable = false)
	private String receiverId;

	@Column(nullable = false)
	private long amount;

	@Column(nullable = false, length = 400)
	private String reason;

	@Column(nullable = false, length = 1000)
	private String description;

	@Column(nullable = false, length = 16)
	private String state;

	@Column(name = "report_id")
	private String reportId;

	@Column(name = "review_note", length = 500)
	private String reviewNote;

	@Column(name = "reviewed_by")
	private String reviewedBy;

	@Column(name = "reviewed_at")
	private Instant reviewedAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	static CoinDisputeJpaEntity from(CoinDispute d) {
		CoinDisputeJpaEntity e = new CoinDisputeJpaEntity();
		e.id = d.id();
		e.entryRef = d.entryRef();
		e.senderId = d.senderId();
		e.receiverId = d.receiverId();
		e.amount = d.amount();
		e.reason = d.reason();
		e.description = d.description();
		e.state = d.state().name();
		e.reportId = d.reportId();
		e.reviewNote = d.reviewNote();
		e.reviewedBy = d.reviewedBy();
		e.reviewedAt = d.reviewedAt();
		e.createdAt = d.createdAt();
		return e;
	}

	CoinDispute toDomain() {
		return CoinDispute.restore(id, entryRef, senderId, receiverId, amount, reason, description,
			CoinDispute.State.valueOf(state), reportId, reviewNote, reviewedBy, reviewedAt, createdAt);
	}
}
