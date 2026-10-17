package com.socialcoisa.rede.gifs.application;

import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.socialcoisa.rede.gifs.application.port.out.GifProvider;

/**
 * Busca de GIF do seletor de comentario.
 *
 * O primeiro provedor DISPONIVEL responde (o Tenor vem antes do Giphy pela ordem dos beans) — nao
 * mistura os dois: duas fontes numa grade so confunde, e a atribuicao exige dizer de quem e o GIF.
 * Com cache curto (ver CacheConfig) o termo repetido nao gasta a cota do provedor de novo.
 */
@Service
public class GifSearchService {

	private final List<GifProvider> providers;

	GifSearchService(List<GifProvider> providers) {
		this.providers = providers;
	}

	@Cacheable(cacheNames = "gifSearch", key = "#query + '|' + #limit")
	public GifSearchResult search(String query, int limit) {
		for (GifProvider provider : providers) {
			if (provider.available()) {
				return new GifSearchResult(true, provider.name(), provider.search(query, limit));
			}
		}
		return GifSearchResult.unavailable();
	}
}
