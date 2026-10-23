package com.socialcoisa.rede.apiv1.origins;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.util.StringUtils;

/**
 * Comparação de origens com suporte a curinga de subdomínio (ex.: {@code https://*.b7cloud.com.br}).
 *
 * As origens já chegam normalizadas (sem barra final, host minúsculo) — o curinga é só para a
 * allowlist de confiança, nunca para o que o desenvolvedor cadastra.
 */
final class OriginPatterns {

	private OriginPatterns() {
	}

	static Set<String> parse(String raw) {
		return Arrays.stream(raw == null ? new String[0] : raw.split(","))
			.map(String::trim)
			.filter(StringUtils::hasText)
			.map(OriginPatterns::normalize)
			.collect(Collectors.toUnmodifiableSet());
	}

	static String normalize(String pattern) {
		String clean = pattern.trim().toLowerCase(Locale.ROOT);
		while (clean.endsWith("/")) {
			clean = clean.substring(0, clean.length() - 1);
		}
		return clean;
	}

	static boolean matches(String origin, Set<String> patterns) {
		for (String pattern : patterns) {
			if (matches(origin, pattern)) {
				return true;
			}
		}
		return false;
	}

	static boolean matches(String origin, String pattern) {
		if (pattern.equals(origin)) {
			return true;
		}
		if ("*".equals(pattern)) {
			return true;
		}
		int star = pattern.indexOf('*');
		if (star < 0) {
			return false;
		}
		String prefix = pattern.substring(0, star);
		String suffix = pattern.substring(star + 1);
		// O trecho curinga precisa ser um host de verdade: "https://.b7cloud.com.br" não vale.
		return origin.startsWith(prefix) && origin.endsWith(suffix)
				&& origin.length() > prefix.length() + suffix.length();
	}
}
