package com.socialcoisa.rede.shared.web;

import java.net.URI;
import java.util.List;
import java.util.Locale;

import org.springframework.util.StringUtils;

/**
 * Valida links públicos informados pelo usuário (portfólio, links externos, site de empresa).
 *
 * O front renderiza esses valores em {@code href}; aceitar {@code javascript:}, {@code data:} ou
 * {@code vbscript:} transformava o perfil em vetor de XSS armazenado (E-07/E-23). Só http(s) com
 * host; nada de esquema relativo, credenciais embutidas ou controle no meio.
 */
public final class SafeUrl {

	private SafeUrl() {
	}

	public static String requireHttpUrl(String raw, String field) {
		if (!StringUtils.hasText(raw)) {
			return raw;
		}
		String clean = raw.trim();
		if (clean.length() > 500 || clean.chars().anyMatch(Character::isISOControl)) {
			throw invalid(field);
		}
		URI uri;
		try {
			uri = URI.create(clean);
		}
		catch (RuntimeException malformed) {
			throw invalid(field);
		}
		String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
		if (!"http".equals(scheme) && !"https".equals(scheme)) {
			throw invalid(field);
		}
		if (!StringUtils.hasText(uri.getHost()) || uri.getUserInfo() != null) {
			throw invalid(field);
		}
		return clean;
	}

	public static List<String> requireHttpUrls(List<String> raw, String field) {
		if (raw == null) {
			return null;
		}
		return raw.stream().map(link -> requireHttpUrl(link, field)).toList();
	}

	private static ApiException invalid(String field) {
		return new ApiException(400, "INVALID_URL",
			"O campo " + field + " precisa ser um link http(s) valido.");
	}
}
