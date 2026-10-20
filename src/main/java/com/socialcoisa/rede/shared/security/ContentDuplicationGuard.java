package com.socialcoisa.rede.shared.security;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Rejeita conteúdo duplicado do mesmo usuário dentro de uma janela.
 *
 * O hash do texto normalizado é armazenado por uma hora (configurável em
 * {@code rede.rate.duplicate-window-minutes}; 0 desliga): se o mesmo usuário tentar publicar o
 * mesmo texto de novo nesse intervalo, a requisição é rejeitada com 409. Isso barra o caso mais
 * comum de spam (mesmo texto colado em loop) sem impedir edições ou republicações após o período.
 */
@Component
public class ContentDuplicationGuard {

	private static final int MAX_ENTRIES = 50_000;

	private final Cache<String, Boolean> seen;
	private final long windowMinutes;

	public ContentDuplicationGuard(
			@Value("${rede.rate.duplicate-window-minutes:60}") long windowMinutes) {
		this.windowMinutes = windowMinutes;
		if (windowMinutes > 0) {
			this.seen = Caffeine.newBuilder()
				.expireAfterWrite(Duration.ofMinutes(windowMinutes))
				.maximumSize(MAX_ENTRIES)
				.build();
		}
		else {
			this.seen = null;
		}
	}

	/**
	 * Verifica se o conteúdo já foi publicado por este usuário recentemente. Lança 409 se duplicado.
	 */
	public void require(String userId, String content) {
		if (seen == null) {
			return;
		}
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
