package com.socialcoisa.rede.feed.application;

import java.util.List;

import com.socialcoisa.rede.feed.domain.PostSealType;

/**
 * Os selos de um post: as contagens (do maior para o menor) e os selos de QUEM esta lendo.
 *
 * `seals` vazio = ninguem marcou nada ainda. `mySeals` e uma LISTA porque a mesma pessoa pode marcar
 * o post com mais de um selo — o front acende todos os dela no seletor.
 */
public record SealsView(List<SealCount> seals, List<PostSealType> mySeals) {

	public record SealCount(PostSealType seal, int count) {
	}

	public static SealsView empty() {
		return new SealsView(List.of(), List.of());
	}
}
