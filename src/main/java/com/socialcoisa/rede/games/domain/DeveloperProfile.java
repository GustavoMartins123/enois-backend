package com.socialcoisa.rede.games.domain;

import java.time.Instant;

/**
 * A página pública do criador no Games.
 *
 * Presa à conta do Énóis pelo `userId` (chave primária): não é uma segunda identidade — é a mesma
 * pessoa, com o que ela quer mostrar QUEM JOGA. Nome, foto e @ vêm do perfil do Énóis; aqui ficam o
 * nome de estúdio, a chamada, a bio e os links.
 */
public record DeveloperProfile(String userId, String displayName, String headline, String bio, String links,
		String avatar, String cover, Instant updatedAt) {

	public static DeveloperProfile create(String userId, Instant now) {
		return new DeveloperProfile(userId, null, null, null, null, null, null, now);
	}

	public DeveloperProfile update(String displayName, String headline, String bio, String links, String avatar,
			String cover, Instant now) {
		return new DeveloperProfile(userId, displayName, headline, bio, links, avatar, cover, now);
	}

	/** Só existe página de criador quando a pessoa preencheu alguma coisa. */
	public boolean isFilled() {
		return displayName != null || headline != null || bio != null || links != null;
	}
}
