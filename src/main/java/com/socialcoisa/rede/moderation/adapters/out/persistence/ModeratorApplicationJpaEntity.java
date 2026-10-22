package com.socialcoisa.rede.moderation.adapters.out.persistence;

import java.time.Instant;

import com.socialcoisa.rede.moderation.domain.ModeratorApplication;
import com.socialcoisa.rede.moderation.domain.ModeratorApplicationStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "moderator_applications", indexes = {
	@Index(name = "idx_mod_app_user", columnList = "user_id, created_at"),
	@Index(name = "idx_mod_app_status", columnList = "status, created_at") })
class ModeratorApplicationJpaEntity {

	@Id
	private String id;

	@Column(name = "user_id", nullable = false)
	private String userId;

	@Column(nullable = false, length = 2000)
	private String motivation;

	@Column(length = 2000)
	private String experience;

	@Column(length = 500)
	private String availability;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ModeratorApplicationStatus status;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "reviewed_by")
	private String reviewedBy;

	@Column(name = "reviewed_at")
	private Instant reviewedAt;

	@Column(name = "review_note", length = 1000)
	private String reviewNote;

	protected ModeratorApplicationJpaEntity() {
	}

	static ModeratorApplicationJpaEntity fromDomain(ModeratorApplication application) {
		ModeratorApplicationJpaEntity e = new ModeratorApplicationJpaEntity();
		e.id = application.id();
		e.userId = application.userId();
		e.motivation = application.motivation();
		e.experience = application.experience();
		e.availability = application.availability();
		e.status = application.status();
		e.createdAt = application.createdAt();
		e.reviewedBy = application.reviewedBy();
		e.reviewedAt = application.reviewedAt();
		e.reviewNote = application.reviewNote();
		return e;
	}

	ModeratorApplication toDomain() {
		return ModeratorApplication.restore(id, userId, motivation, experience, availability, status, createdAt,
			reviewedBy, reviewedAt, reviewNote);
	}
}
