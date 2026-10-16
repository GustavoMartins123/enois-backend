package com.socialcoisa.rede.feed.domain;

/**
 * Categorias de publicacao.
 *
 * ARTICLE e categoria propria: o corpo dele (ate 4.000 caracteres) fica na tabela `articles`, e o
 * `content` do post guarda titulo/resumo (ver PublishPostService). PAGE e o post com uma mini pagina
 * em HTML sanitizada no servidor. Os demais tipos sao texto curto, como sempre foram.
 */
public enum PostType {
	INSIGHT,
	ARTICLE,
	QUESTION,
	PROJECT,
	OPPORTUNITY,
	/** Mini pagina em HTML (colada pelo autor e sanitizada no backend). */
	PAGE,
	NEWS,
	TUTORIAL,
	REVIEW,
	MOMENT
}
