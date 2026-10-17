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
 * Tenor API v2 (Google) — busca e destaques. Precisa de chave (Google Cloud com a Tenor API
 * habilitada); sem ela o provedor fica indisponivel e o seletor avisa em vez de fingir vazio.
 *
 * Termo vazio consulta os destaques: abrir o seletor ja mostra GIF, sem obrigar a digitar.
 */
public class TenorGifProvider implements GifProvider {

	private static final String BASE = "https://tenor.googleapis.com/v2";

	private final String apiKey;
	private final ObjectMapper objectMapper;
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

	public TenorGifProvider(String apiKey, ObjectMapper objectMapper) {
		this.apiKey = apiKey;
		this.objectMapper = objectMapper;
	}

	@Override
	public String name() {
		return "Tenor";
	}

	@Override
	public boolean available() {
		return StringUtils.hasText(apiKey);
	}

	@Override
	public List<GifItem> search(String query, int limit) {
		boolean hasQuery = StringUtils.hasText(query);
		StringBuilder url = new StringBuilder(BASE).append(hasQuery ? "/search" : "/featured");
		url.append("?key=").append(URLEncoder.encode(apiKey, StandardCharsets.UTF_8));
		url.append("&limit=").append(limit);
		url.append("&media_filter=gif,tinygif");
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

	/** Mapeia a resposta do Tenor; separado para o teste cobrir o formato sem rede. */
	static List<GifItem> map(JsonNode body) {
		List<GifItem> items = new ArrayList<>();
		for (JsonNode node : body.path("results")) {
			JsonNode formats = node.path("media_formats");
			String url = text(formats.path("gif").path("url"));
			String preview = text(formats.path("tinygif").path("url"));
			if (!StringUtils.hasText(url)) {
				url = preview;
			}
			if (!StringUtils.hasText(url)) {
				continue;
			}
			JsonNode dims = formats.path("gif").path("dims");
			items.add(new GifItem(text(node.path("id")), url,
				StringUtils.hasText(preview) ? preview : url,
				dims.path(0).asInt(0), dims.path(1).asInt(0), text(node.path("content_description")), "Tenor"));
		}
		return items;
	}

	private static String text(JsonNode node) {
		if (node.isMissingNode() || node.isNull()) {
			return null;
		}
		String value = node.asText();
		return StringUtils.hasText(value) ? value : null;
	}
}
