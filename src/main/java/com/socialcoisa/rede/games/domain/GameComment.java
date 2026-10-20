package com.socialcoisa.rede.games.domain;

import java.time.Instant;

/** Comentário na página de um jogo. */
public record GameComment(String id, String gameId, String userId, String body, Instant createdAt) {

	public static GameComment create(String id, String gameId, String userId, String body, Instant now) {
		return new GameComment(id, gameId, userId, body, now);
	}
}
