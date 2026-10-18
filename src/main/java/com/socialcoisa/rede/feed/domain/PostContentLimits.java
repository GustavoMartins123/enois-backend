package com.socialcoisa.rede.feed.domain;

import java.util.Map;

/**
 * Quanto texto cada TIPO de post aceita.
 *
 * O limite e por tipo porque o tipo e o formato da conversa: uma duvida e uma pergunta curta, um
 * tutorial e um texto de leitura. Um numero unico obrigava a escolher entre apertar o tutorial ou
 * deixar a duvida virar redacao.
 *
 * A regra e a mesma na criacao e na edicao (as duas leem daqui) e a tela mostra exatamente este
 * numero — se o campo diz 400, o servidor recusa em 401.
 */
public final class PostContentLimits {

	private static final int PADRAO = 1_200;

	private static final Map<PostType, Integer> POR_TIPO = Map.of(
		PostType.QUESTION, 400,
		PostType.REVIEW, 600,
		PostType.PROJECT, 2_000,
		PostType.NEWS, 2_000,
		PostType.OPPORTUNITY, 1_500,
		PostType.TUTORIAL, 5_000);

	private PostContentLimits() {
	}

	public static int of(PostType type) {
		return type == null ? PADRAO : POR_TIPO.getOrDefault(type, PADRAO);
	}
}
