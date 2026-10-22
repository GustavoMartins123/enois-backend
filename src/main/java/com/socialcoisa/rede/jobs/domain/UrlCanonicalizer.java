package com.socialcoisa.rede.jobs.domain;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Normaliza uma URL para comparar a MESMA oportunidade entre fontes diferentes: minúsculas no host,
 * sem fragmento, sem parâmetros de rastreio (utm_*, gclid, etc.), sem barra final. Não resolve
 * redirecionamentos (isso seria rede) — é uma chave estável de comparação.
 */
public final class UrlCanonicalizer {

	private static final Set<String> TRACKING = Set.of("gclid", "fbclid", "msclkid", "ref", "referrer",
		"source", "src", "trk", "trackingid");

	private UrlCanonicalizer() {
	}

	public static String canonicalize(String rawUrl) {
		if (rawUrl == null || rawUrl.isBlank()) {
			return null;
		}
		try {
			URI uri = URI.create(rawUrl.trim());
			String scheme = uri.getScheme() == null ? "https" : uri.getScheme().toLowerCase(Locale.ROOT);
			String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT)
				.replaceFirst("^www\\.", "");
			String path = uri.getPath() == null ? "" : uri.getPath();
			if (path.length() > 1 && path.endsWith("/")) {
				path = path.substring(0, path.length() - 1);
			}
			String query = canonicalQuery(uri.getRawQuery());
			return scheme + "://" + host + path + query;
		}
		catch (RuntimeException invalid) {
			// URL invalida: usa o texto aparado como chave (nao inventa).
			return rawUrl.trim().toLowerCase(Locale.ROOT);
		}
	}

	private static String canonicalQuery(String rawQuery) {
		if (rawQuery == null || rawQuery.isBlank()) {
			return "";
		}
		String kept = Arrays.stream(rawQuery.split("&"))
			.filter(pair -> {
				String key = pair.split("=", 2)[0].toLowerCase(Locale.ROOT);
				return !key.startsWith("utm_") && !TRACKING.contains(key);
			})
			.sorted()
			.map(pair -> URLDecoder.decode(pair, StandardCharsets.UTF_8))
			.collect(Collectors.joining("&"));
		return kept.isEmpty() ? "" : "?" + kept;
	}
}
