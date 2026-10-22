package com.socialcoisa.rede.games.application;

import com.socialcoisa.rede.games.domain.Game;

/**
 * O card de "jogados hoje": o mínimo para abrir o jogo e mostrar a contagem.
 *
 * Não reaproveita `GameView` de propósito — ela carrega a ficha inteira do jogo, e o card do feed
 * só precisa do endereço (`/games/{username}/{slug}`) e dos números da janela.
 */
public record TrendingGameView(String id, String username, String slug, String title, String cover, String icon,
		String url, long plays, long players) {

	public static TrendingGameView of(GameCatalogService.TrendingGame item, String username) {
		Game game = item.game();
		return new TrendingGameView(game.id(), username, game.slug(), game.title(), game.cover(),
			game.details().icon(), game.url(), item.plays(), item.players());
	}
}
