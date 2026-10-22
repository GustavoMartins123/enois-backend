package com.socialcoisa.rede.games.application;

import com.socialcoisa.rede.games.domain.GameUpdate;

/** Uma nota do diário de desenvolvimento, como o front mostra. */
public record GameUpdateView(String id, String gameId, String title, String body, String version,
		String createdAt) {

	public static GameUpdateView from(GameUpdate update) {
		return new GameUpdateView(update.id(), update.gameId(), update.title(), update.body(), update.version(),
			update.createdAt().toString());
	}
}
