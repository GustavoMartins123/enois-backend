package com.socialcoisa.rede.groups.adapters.out.persistence;

import java.time.Instant;

import com.socialcoisa.rede.groups.domain.GroupJoinRequest;
import com.socialcoisa.rede.groups.domain.JoinRequestStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "group_join_requests")
class GroupJoinRequestJpaEntity {

	@Id
	private String id;

	@Column(name = "group_id", nullable = false)
	private String groupId;

	@Column(name = "user_id", nullable = false)
	private String userId;

	@Column(length = 500)
	private String message;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private JoinRequestStatus status;

	/** Vale o user_id enquanto PENDENTE e vira NULL ao decidir: é o índice único do pendente. */
	@Column(name = "pending_slot")
	private String pendingSlot;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "decided_by")
	private String decidedBy;

	@Column(name = "decided_at")
	private Instant decidedAt;

	protected GroupJoinRequestJpaEntity() {
	}

	static GroupJoinRequestJpaEntity fromDomain(GroupJoinRequest request) {
		GroupJoinRequestJpaEntity entity = new GroupJoinRequestJpaEntity();
		entity.id = request.id();
		entity.groupId = request.groupId();
		entity.userId = request.userId();
		entity.message = request.message();
		entity.status = request.status();
		entity.pendingSlot = request.isPending() ? request.userId() : null;
		entity.createdAt = request.createdAt();
		entity.decidedBy = request.decidedBy();
		entity.decidedAt = request.decidedAt();
		return entity;
	}

	GroupJoinRequest toDomain() {
		return GroupJoinRequest.restore(id, groupId, userId, message, status, createdAt, decidedBy, decidedAt);
	}
}
