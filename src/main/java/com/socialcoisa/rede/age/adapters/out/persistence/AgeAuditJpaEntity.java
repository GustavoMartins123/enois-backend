package com.socialcoisa.rede.age.adapters.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "age_audit_events")
class AgeAuditJpaEntity {

	@Id
	@Column(name = "id", length = 64, nullable = false)
	private String id;

	@Column(name = "user_id", length = 64, nullable = false)
	private String userId;

	@Column(name = "event", length = 40, nullable = false)
	private String event;

	@Column(name = "actor_id", length = 64)
	private String actorId;

	@Column(name = "detail", length = 500)
	private String detail;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected AgeAuditJpaEntity() {
	}

	static AgeAuditJpaEntity create(String id, String userId, String event, String actorId, String detail,
			Instant createdAt) {
		AgeAuditJpaEntity entity = new AgeAuditJpaEntity();
		entity.id = id;
		entity.userId = userId;
		entity.event = event;
		entity.actorId = actorId;
		entity.detail = detail;
		entity.createdAt = createdAt;
		return entity;
	}
}
