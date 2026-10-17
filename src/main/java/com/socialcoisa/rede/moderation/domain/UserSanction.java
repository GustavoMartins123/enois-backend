package com.socialcoisa.rede.moderation.domain;

import java.time.Instant;

/**
 * Sancao de um usuario por moderacao. RESTRICTION trava publicar/comentar e pode ser revista
 * (recurso); BAN e permanente e vem do recurso NEGADO por um humano — a IA nunca bane.
 */
public record UserSanction(String id, String userId, String reportId, SanctionType type, String reason,
		SanctionStatus status, Instant createdAt, Instant removedAt, String removedBy) {

	public static UserSanction create(String id, String userId, String reportId, String reason, Instant now) {
		return new UserSanction(id, userId, reportId, SanctionType.RESTRICTION, reason, SanctionStatus.ACTIVE,
			now, null, null);
	}

	public static UserSanction restore(String id, String userId, String reportId, SanctionType type, String reason,
			SanctionStatus status, Instant createdAt, Instant removedAt, String removedBy) {
		return new UserSanction(id, userId, reportId, type, reason, status, createdAt, removedAt, removedBy);
	}

	public boolean isActive() {
		return status == SanctionStatus.ACTIVE;
	}

	public boolean isBan() {
		return type == SanctionType.BAN;
	}

	/** Recurso aceito: restricao sai e a conta volta ao normal. */
	public UserSanction remove(String reviewerId, Instant at) {
		return new UserSanction(id, userId, reportId, type, reason, SanctionStatus.REMOVED, createdAt, at, reviewerId);
	}

	/** Recurso negado: restricao vira BAN permanente. */
	public UserSanction escalateToBan() {
		return new UserSanction(id, userId, reportId, SanctionType.BAN, reason, SanctionStatus.ACTIVE, createdAt,
			removedAt, removedBy);
	}
}
