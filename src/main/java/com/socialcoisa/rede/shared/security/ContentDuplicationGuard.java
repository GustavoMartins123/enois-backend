package com.socialcoisa.rede.shared.security;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Rejeita conteúdo duplicado do mesmo usuário dentro de uma janela.
 *
 * O hash do texto normalizado é armazenado por uma hora: se o mesmo usuário tentar publicar o
 * mesmo texto de novo nesse intervalo, a requisição é rejeitada com 409. Isso barra o caso mais
 * comum de spam (mesmo texto colado em loop) sem impedir edições ou republicações após o período.
 */
@Component
public class ContentDuplicationGuard {

	private static final Duration WINDOW = Duration.ofHours(1);
	private static final int MAX_ENTRIES = 50_000;

	private final Cache<String, Boolean> seen = Caffeine.newBuilder()
		.expireAfterWrite(WINDOW)
		.maximumSize(MAX_ENTRIES)
		.build();

	/**
	 * Verifica se o conteúdo já foi publicado por este usuário recentemente. Lança 409 se duplicado.
	 */
	public void require(String userId, String content) {
		String hash = Hashes.sha256Hex(normalize(content));
		String key = userId + "|" + hash;
		if (seen.getIfPresent(key) != null) {
			throw new ApiException(409, "DUPLICATE_CONTENT",
				"Voce ja publicou este conteudo recentemente.");
		}
		seen.put(key, Boolean.TRUE);
	}

	private static String normalize(String text) {
		return text.strip().toLowerCase().replaceAll("\\s+", " ");
	}
}
