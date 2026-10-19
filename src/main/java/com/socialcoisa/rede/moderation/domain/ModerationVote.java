package com.socialcoisa.rede.moderation.domain;

import java.time.Instant;

/** Um voto por moderador por denuncia; o painel permite trocar o voto enquanto ela esta PENDING. */
public record ModerationVote(String id, String reportId, String moderatorId, ModerationVoteType vote,
		String note, Instant createdAt, Instant updatedAt) {

	public static ModerationVote create(String id, String reportId, String moderatorId, ModerationVoteType vote,
			String note, Instant now) {
		return new ModerationVote(id, reportId, moderatorId, vote, note, now, now);
	}

	public static ModerationVote restore(String id, String reportId, String moderatorId, ModerationVoteType vote,
			String note, Instant createdAt, Instant updatedAt) {
		return new ModerationVote(id, reportId, moderatorId, vote, note, createdAt, updatedAt);
	}

	public ModerationVote changeTo(ModerationVoteType newVote, String newNote, Instant at) {
		return new ModerationVote(id, reportId, moderatorId, newVote, newNote, createdAt, at);
	}
}
