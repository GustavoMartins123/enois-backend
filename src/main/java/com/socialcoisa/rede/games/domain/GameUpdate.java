package com.socialcoisa.rede.games.domain;

import java.time.Instant;

/** Uma nota do diário de desenvolvimento: o que mudou nesta versão do jogo. */
public record GameUpdate(String id, String gameId, String userId, String title, String body, String version,
		Instant createdAt) {

	public static GameUpdate create(String id, String gameId, String userId, String title, String body,
			String version, Instant now) {
		return new GameUpdate(id, gameId, userId, title, body, version, now);
	}
}
