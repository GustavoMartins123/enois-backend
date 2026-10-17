package com.socialcoisa.rede.gifs.adapters.out.providers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import com.socialcoisa.rede.gifs.application.port.out.GifProvider;

import tools.jackson.databind.ObjectMapper;

/**
 * Provedores do seletor de GIF.
 *
 * Os dois nascem como bean mesmo sem chave — quem responde pela ausencia e o `available()` de cada
 * um. Assim a falta de configuracao vira uma mensagem no seletor, e nao um erro de boot (a licao do
 * storage: condicional demais quebrou o pod uma vez). A ordem importa: com as duas chaves, o Tenor
 * responde primeiro.
 *
 * Chaves e como conseguir: README, secao "Busca de GIF".
 */
@Configuration(proxyBeanMethods = false)
class GifProvidersConfig {

	@Bean
	@Order(1)
	GifProvider tenorGifProvider(@Value("${rede.gifs.tenor-key:}") String apiKey, ObjectMapper objectMapper) {
		return new TenorGifProvider(apiKey, objectMapper);
	}

	@Bean
	@Order(2)
	GifProvider giphyGifProvider(@Value("${rede.gifs.giphy-key:}") String apiKey, ObjectMapper objectMapper) {
		return new GiphyGifProvider(apiKey, objectMapper);
	}
}
