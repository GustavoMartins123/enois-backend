package com.socialcoisa.rede.moderation.adapters.out.persistence;

import java.time.Instant;

import com.socialcoisa.rede.moderation.domain.Appeal;
import com.socialcoisa.rede.moderation.domain.AppealStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "appeals", indexes = {
		@Index(name = "idx_sanction_appeals_status", columnList = "status, created_at"),
		@Index(name = "idx_appeals_user", columnList = "user_id") })
class AppealJpaEntity {

	@Id
	private String id;

	@Column(name = "sanction_id", nullable = false)
	private String sanctionId;

	@Column(name = "user_id", nullable = false)
	private String userId;

	@Column(nullable = false, length = 2000)
	private String message;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AppealStatus status;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "reviewed_by")
	private String reviewedBy;

	@Column(name = "reviewed_at")
	private Instant reviewedAt;

	protected AppealJpaEntity() {
	}

	static AppealJpaEntity fromDomain(Appeal appeal) {
		AppealJpaEntity e = new AppealJpaEntity();
		e.id = appeal.id();
		e.sanctionId = appeal.sanctionId();
		e.userId = appeal.userId();
		e.message = appeal.message();
		e.status = appeal.status();
		e.createdAt = appeal.createdAt();
		e.reviewedBy = appeal.reviewedBy();
		e.reviewedAt = appeal.reviewedAt();
		return e;
	}

	Appeal toDomain() {
		return Appeal.restore(id, sanctionId, userId, message, status, createdAt, reviewedBy, reviewedAt);
	}
}
