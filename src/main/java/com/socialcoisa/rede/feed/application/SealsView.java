package com.socialcoisa.rede.feed.application;

import java.util.List;

import com.socialcoisa.rede.feed.domain.PostSealType;

/**
 * Os selos de um post: as contagens (do maior para o menor) e o selo de QUEM esta lendo, quando ha
 * um. `seals` vazio = ninguem marcou nada ainda.
 */
public record SealsView(List<SealCount> seals, PostSealType mySeal) {

	public record SealCount(PostSealType seal, int count) {
	}

	public static SealsView empty() {
		return new SealsView(List.of(), null);
	}
}
