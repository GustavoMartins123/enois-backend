package com.socialcoisa.rede.jobs.domain;

import java.time.Instant;

/**
 * Vaga de plataforma externa (ex.: JobRadar). Nao e uma vaga criada na plataforma: fica em tabela
 * propria e so e LISTADA no enois, com a candidatura na origem. O que a fonte nao fornece fica nulo
 * (empresa/local/salario) — nada e inventado.
 */
public record ExternalJob(String id, String platform, String externalId, String title, String company,
		String location, String salary, String currency, String url, Instant importedAt, Instant lastCheckedAt,
		String status, String canonicalUrl) {

	public static final String FOUND = "FOUND";
	public static final String POSSIBLY_CLOSED = "POSSIBLY_CLOSED";

	/** Chave de deduplicacao entre fontes: a URL normalizada (ver UrlCanonicalizer). */
	public String dedupeKey() {
		return canonicalUrl != null && !canonicalUrl.isBlank() ? canonicalUrl : url;
	}
}
