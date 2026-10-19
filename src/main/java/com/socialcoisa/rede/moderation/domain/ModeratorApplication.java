package com.socialcoisa.rede.moderation.domain;

import java.time.Instant;

/**
 * Candidatura a moderador. A aprovacao nao vira coluna em `users`: a candidatura APPROVED vigente
 * e a fonte do papel, e revogar e mudar o estado (nunca DELETE).
 */
public record ModeratorApplication(String id, String userId, String motivation, String experience,
		String availability, ModeratorApplicationStatus status, Instant createdAt, String reviewedBy,
		Instant reviewedAt, String reviewNote) {

	public static ModeratorApplication create(String id, String userId, String motivation, String experience,
			String availability, Instant now) {
		return new ModeratorApplication(id, userId, motivation, experience, availability,
			ModeratorApplicationStatus.PENDING, now, null, null, null);
	}

	public static ModeratorApplication restore(String id, String userId, String motivation, String experience,
			String availability, ModeratorApplicationStatus status, Instant createdAt, String reviewedBy,
			Instant reviewedAt, String reviewNote) {
		return new ModeratorApplication(id, userId, motivation, experience, availability, status, createdAt,
			reviewedBy, reviewedAt, reviewNote);
	}

	public boolean isPending() {
		return status == ModeratorApplicationStatus.PENDING;
	}

	public ModeratorApplication approve(String reviewerId, String note, Instant at) {
		return new ModeratorApplication(id, userId, motivation, experience, availability,
			ModeratorApplicationStatus.APPROVED, createdAt, reviewerId, at, note);
	}

	public ModeratorApplication reject(String reviewerId, String note, Instant at) {
		return new ModeratorApplication(id, userId, motivation, experience, availability,
			ModeratorApplicationStatus.REJECTED, createdAt, reviewerId, at, note);
	}

	public ModeratorApplication revoke(String reviewerId, String note, Instant at) {
		return new ModeratorApplication(id, userId, motivation, experience, availability,
			ModeratorApplicationStatus.REVOKED, createdAt, reviewerId, at, note);
	}
}
