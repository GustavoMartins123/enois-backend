package com.socialcoisa.rede.games.application;

import java.util.List;

import com.socialcoisa.rede.games.domain.Game;

/**
 * O jogo como o front precisa: o ciclo de vida, o endereço público e a ficha da página.
 *
 * Uma view só para o catálogo, a página do jogo e o painel — o que muda entre as telas é o que o
 * front mostra, não o formato do dado. `username` vem resolvido do perfil (o endereço público é
 * `/games/{username}/{slug}`, e o jogo não guarda o @ de ninguém).
 */
public record GameView(String id, String userId, String username, String slug, String title, String description,
		String cover, String url, String tags, String status, String reviewNote, String submittedAt,
		String shortDescription, List<String> genres, List<String> languages, String controls, String requirements,
		String contentWarnings, String stage, String distribution, int priceCoins, String aspect, String version,
		String websiteUrl, String supportUrl, List<String> mediaIds, int plays, int favorites, String publishedAt,
		boolean owned, boolean favorited) {

	/** Monta a view do jogo. `favorites` e `favorited` só são preenchidos onde fazem sentido. */
	public static GameView of(Game game, String username, int favorites, boolean favorited, String viewerId) {
		var details = game.details();
		return new GameView(game.id(), game.userId(), username, game.slug(), game.title(), game.description(),
			game.cover(), game.url(), game.tags(), game.status().name(), game.reviewNote(),
			game.submittedAt().toString(), details.shortDescription(), split(details.genres()),
			split(details.languages()), details.controls(), details.requirements(), details.contentWarnings(),
			details.stage().name(), details.distribution().name(), details.priceCoins(), details.aspect().name(),
			details.version(), details.websiteUrl(), details.supportUrl(), details.mediaIdList(), game.plays(),
			favorites, game.publishedAt() == null ? null : game.publishedAt().toString(),
			viewerId != null && game.ownedBy(viewerId), favorited);
	}

	public static GameView of(Game game, String username) {
		return of(game, username, 0, false, null);
	}

	/** Listas viajam como texto separado por vírgula no banco (convenção das tags) e como lista aqui. */
	private static List<String> split(String value) {
		if (value == null || value.isBlank()) {
			return List.of();
		}
		return java.util.Arrays.stream(value.split(",")).map(String::trim).filter(v -> !v.isEmpty()).toList();
	}
}
