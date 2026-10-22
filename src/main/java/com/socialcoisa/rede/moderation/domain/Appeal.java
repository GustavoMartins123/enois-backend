package com.socialcoisa.rede.moderation.domain;

import java.time.Instant;

/** Recurso de uma sancao. Aprovado = restricao sai; negado = conta banida. Decisao sempre humana. */
public record Appeal(String id, String sanctionId, String userId, String message, AppealStatus status,
		Instant createdAt, String reviewedBy, Instant reviewedAt) {

	public static Appeal create(String id, String sanctionId, String userId, String message, Instant now) {
		return new Appeal(id, sanctionId, userId, message, AppealStatus.PENDING, now, null, null);
	}

	public static Appeal restore(String id, String sanctionId, String userId, String message, AppealStatus status,
			Instant createdAt, String reviewedBy, Instant reviewedAt) {
		return new Appeal(id, sanctionId, userId, message, status, createdAt, reviewedBy, reviewedAt);
	}

	public boolean isPending() {
		return status == AppealStatus.PENDING;
	}

	public Appeal approve(String reviewerId, Instant at) {
		return new Appeal(id, sanctionId, userId, message, AppealStatus.APPROVED, createdAt, reviewerId, at);
	}

	public Appeal deny(String reviewerId, Instant at) {
		return new Appeal(id, sanctionId, userId, message, AppealStatus.DENIED, createdAt, reviewerId, at);
	}
}
