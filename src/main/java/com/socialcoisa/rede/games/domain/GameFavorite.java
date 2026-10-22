package com.socialcoisa.rede.games.domain;

import java.time.Instant;

/** Jogo guardado na biblioteca de quem joga (favorito). */
public record GameFavorite(String id, String userId, String gameId, Instant createdAt) {

	public static GameFavorite create(String id, String userId, String gameId, Instant now) {
		return new GameFavorite(id, userId, gameId, now);
	}
}
