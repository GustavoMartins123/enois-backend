package com.socialcoisa.rede.groups.domain;

import java.time.Instant;

/**
 * Pedido de ingresso num grupo que exige aprovação.
 *
 * Nasce PENDENTE e a decisão é de quem administra; o pedido decidido vira histórico — pedir de
 * novo depois de recusado cria outro pedido, não reabre o antigo. O banco garante UM pendente por
 * pessoa e grupo (`pending_slot`), então duas requisições simultâneas não viram dois pedidos.
 */
public class GroupJoinRequest {

	private final String id;
	private final String groupId;
	private final String userId;
	private final String message;
	private final JoinRequestStatus status;
	private final Instant createdAt;
	private final String decidedBy;
	private final Instant decidedAt;

	private GroupJoinRequest(String id, String groupId, String userId, String message, JoinRequestStatus status,
			Instant createdAt, String decidedBy, Instant decidedAt) {
		this.id = id;
		this.groupId = groupId;
		this.userId = userId;
		this.message = message;
		this.status = status;
		this.createdAt = createdAt;
		this.decidedBy = decidedBy;
		this.decidedAt = decidedAt;
	}

	public static GroupJoinRequest create(String id, String groupId, String userId, String message, Instant now) {
		return new GroupJoinRequest(id, groupId, userId, message, JoinRequestStatus.PENDING, now, null, null);
	}

	public static GroupJoinRequest restore(String id, String groupId, String userId, String message,
			JoinRequestStatus status, Instant createdAt, String decidedBy, Instant decidedAt) {
		return new GroupJoinRequest(id, groupId, userId, message, status, createdAt, decidedBy, decidedAt);
	}

	public GroupJoinRequest approve(String actorId, Instant now) {
		return new GroupJoinRequest(id, groupId, userId, message, JoinRequestStatus.APPROVED, createdAt, actorId, now);
	}

	public GroupJoinRequest reject(String actorId, Instant now) {
		return new GroupJoinRequest(id, groupId, userId, message, JoinRequestStatus.REJECTED, createdAt, actorId, now);
	}

	public boolean isPending() {
		return status == JoinRequestStatus.PENDING;
	}

	public String id() {
		return id;
	}

	public String groupId() {
		return groupId;
	}

	public String userId() {
		return userId;
	}

	public String message() {
		return message;
	}

	public JoinRequestStatus status() {
		return status;
	}

	public Instant createdAt() {
		return createdAt;
	}

	public String decidedBy() {
		return decidedBy;
	}

	public Instant decidedAt() {
		return decidedAt;
	}
}
