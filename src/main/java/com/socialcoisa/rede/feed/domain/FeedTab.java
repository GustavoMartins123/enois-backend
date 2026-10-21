package com.socialcoisa.rede.feed.domain;

/**
 * As abas do feed. Cada tipo de post virou uma forma de navegar (pedido da dona, 2026-10-20:
 * "cria mais tipos de feed"): DISCUSSIONS já era QUESTION e PROJECTS já era PROJECT; agora
 * NOTÍCIAS, TUTORIAIS, OPORTUNIDADES, AVALIAÇÕES e DIVERSÃO também têm casa. FOR_YOU/FOLLOWING
 * são recortes de relação, ALL é tudo e RECENT é só ordenação.
 */
public enum FeedTab {
	FOR_YOU,
	FOLLOWING,
	DISCUSSIONS,
	PROJECTS,
	NEWS,
	TUTORIALS,
	OPPORTUNITIES,
	REVIEWS,
	FUN,
	ALL,
	RECENT,
	/**
	 * "Ainda nao vi": o oposto de DISCUSSIONS — os posts em que a pessoa ainda nao reagiu nem
	 * comentou (pedido da dona, 2026-10-21).
	 */
	UNSEEN
}
