package com.socialcoisa.rede.games.application;

import java.util.List;

/**
 * A página do criador: identidade do Énóis + o que é do Games + os jogos publicados.
 *
 * `following` é de quem está lendo (false sem sessão), e `owner` diz se é a própria página — o front
 * usa isso para trocar o botão de acompanhar por "editar página".
 */
public record DeveloperPageView(String userId, String username, String name, String badge, String avatar,
		String cover, String displayName, String headline, String bio, List<String> links, List<GameView> games,
		long followers, boolean following, boolean owner) {

	public static DeveloperPageView from(DeveloperProfileService.DeveloperPage page, String badge) {
		var profile = page.profile();
		return new DeveloperPageView(page.user().id(), page.user().username(), page.user().name(), badge,
			profile == null ? page.user().avatar() : firstNonBlank(profile.avatar(), page.user().avatar()),
			profile == null ? null : profile.cover(),
			profile == null ? null : profile.displayName(),
			profile == null ? null : profile.headline(),
			profile == null ? null : profile.bio(),
			split(profile == null ? null : profile.links()),
			page.games().stream().map(game -> GameView.of(game, page.user().username())).toList(),
			page.followers(), page.following(), page.owner());
	}

	private static String firstNonBlank(String first, String second) {
		return first == null || first.isBlank() ? second : first;
	}

	private static List<String> split(String value) {
		if (value == null || value.isBlank()) {
			return List.of();
		}
		return java.util.Arrays.stream(value.split("[,\\n]")).map(String::trim).filter(v -> !v.isEmpty()).toList();
	}
}
