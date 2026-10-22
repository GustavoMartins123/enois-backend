package com.socialcoisa.rede.gifs.adapters.out.providers;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.util.StringUtils;

import com.socialcoisa.rede.gifs.application.GifItem;
import com.socialcoisa.rede.gifs.application.port.out.GifProvider;
import com.socialcoisa.rede.shared.web.ApiException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * GIPHY API (v1) — busca e em alta. Precisa de chave (developers.giphy.com); sem ela o provedor fica
 * indisponivel. `rating=pg-13` acompanha o tom da comunidade (nada de conteudo adulto no seletor).
 */
public class GiphyGifProvider implements GifProvider {

	private static final String BASE = "https://api.giphy.com/v1/gifs";

	private final String apiKey;
	private final ObjectMapper objectMapper;
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

	public GiphyGifProvider(String apiKey, ObjectMapper objectMapper) {
		this.apiKey = apiKey;
		this.objectMapper = objectMapper;
	}

	@Override
	public String name() {
		return "GIPHY";
	}

	@Override
	public boolean available() {
		return StringUtils.hasText(apiKey);
	}

	@Override
	public List<GifItem> search(String query, int limit) {
		boolean hasQuery = StringUtils.hasText(query);
		StringBuilder url = new StringBuilder(BASE).append(hasQuery ? "/search" : "/trending");
		url.append("?api_key=").append(URLEncoder.encode(apiKey, StandardCharsets.UTF_8));
		url.append("&limit=").append(limit);
		url.append("&rating=pg-13");
		// Doc do GIPHY: bundle de messaging tira stickers/clips da grade — só GIFs de chat.
		url.append("&bundle=messaging_non_clips");
		if (hasQuery) {
			url.append("&q=").append(URLEncoder.encode(query, StandardCharsets.UTF_8));
		}
		return map(fetch(url.toString()));
	}

	private JsonNode fetch(String url) {
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(8))
				.GET()
				.build();
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200) {
				throw providerError();
			}
			return objectMapper.readTree(response.body());
		} catch (ApiException error) {
			throw error;
		} catch (Exception error) {
			throw providerError();
		}
	}

	private static ApiException providerError() {
		return new ApiException(502, "GIF_PROVIDER_ERROR", "A busca de GIF nao respondeu. Tente de novo.");
	}

	/** Mapeia a resposta do GIPHY; separado para o teste cobrir o formato sem rede. */
	static List<GifItem> map(JsonNode body) {
		List<GifItem> items = new ArrayList<>();
		for (JsonNode node : body.path("data")) {
			JsonNode original = node.path("images").path("original");
			String url = text(original.path("url"));
			if (!StringUtils.hasText(url)) {
				continue;
			}
			String preview = text(node.path("images").path("fixed_width").path("url"));
			items.add(new GifItem(text(node.path("id")), url,
				StringUtils.hasText(preview) ? preview : url,
				intValue(original.path("width")), intValue(original.path("height")), text(node.path("title")),
				"GIPHY"));
		}
		return items;
	}

	private static int intValue(JsonNode node) {
		try {
			return Integer.parseInt(node.asText("0"));
		} catch (NumberFormatException error) {
			return 0;
		}
	}

	private static String text(JsonNode node) {
		if (node.isMissingNode() || node.isNull()) {
			return null;
		}
		String value = node.asText();
		return StringUtils.hasText(value) ? value : null;
	}
}
