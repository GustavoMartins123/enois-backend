package com.socialcoisa.rede.games.domain;

import java.util.List;

/**
 * A ficha do jogo: tudo o que o jogador lê para decidir, e o que o criador preenche no painel.
 *
 * É um record separado do `Game` porque são dois assuntos: o `Game` é o ciclo de vida (dono, slug,
 * status, moderação) e este é o CONTEÚDO da página. Listas entram como texto separado por vírgula,
 * a mesma convenção de tags que o resto do Énóis usa.
 */
public record GameDetails(String shortDescription, String genres, String languages, String controls,
		String requirements, String contentWarnings, GameStage stage, GameDistribution distribution, int priceCoins,
		GameAspect aspect, String version, String websiteUrl, String supportUrl, String mediaIds) {

	public static GameDetails empty() {
		return new GameDetails(null, null, null, null, null, null, GameStage.RELEASED, GameDistribution.FREE, 0,
			GameAspect.WIDESCREEN, null, null, null, null);
	}

	public static GameDetails of(String shortDescription, String genres, String languages, String controls,
			String requirements, String contentWarnings, GameStage stage, GameDistribution distribution,
			int priceCoins, GameAspect aspect, String version, String websiteUrl, String supportUrl, String mediaIds) {
		return new GameDetails(shortDescription, genres, languages, controls, requirements, contentWarnings,
			stage == null ? GameStage.RELEASED : stage,
			distribution == null ? GameDistribution.FREE : distribution,
			Math.max(0, priceCoins), aspect == null ? GameAspect.WIDESCREEN : aspect, version, websiteUrl,
			supportUrl, mediaIds);
	}

	/** As capturas de tela, na ordem em que o criador as colocou. */
	public List<String> mediaIdList() {
		if (mediaIds == null || mediaIds.isBlank()) {
			return List.of();
		}
		return java.util.Arrays.stream(mediaIds.split(",")).map(String::trim).filter(v -> !v.isEmpty()).toList();
	}

	/** Os gêneros como lista (o filtro do catálogo casa por igualdade em cada item). */
	public List<String> genreList() {
		return split(genres);
	}

	private static List<String> split(String value) {
		if (value == null || value.isBlank()) {
			return List.of();
		}
		return java.util.Arrays.stream(value.split(",")).map(String::trim).filter(v -> !v.isEmpty()).toList();
	}
}
