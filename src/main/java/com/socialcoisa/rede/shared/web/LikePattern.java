package com.socialcoisa.rede.shared.web;

import java.util.Locale;

/**
 * Padrão "contém" para LIKE sem injeção de curinga (E-32).
 *
 * Uma busca por {@code %} virava {@code %%} e devolvia TODOS os itens. Escapamos {@code \},
 * {@code %} e {@code _} e limitamos o termo; as queries usam {@code escape '!'}.
 */
public final class LikePattern {

	private static final int MAX_TERM = 80;

	private LikePattern() {
	}

	public static String contains(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String term = raw.trim().toLowerCase(Locale.ROOT);
		if (term.length() > MAX_TERM) {
			term = term.substring(0, MAX_TERM);
		}
		// `!` como escape (e não `\`): o literal SQL de uma barra invertida muda entre H2 e
		// MariaDB e fazia o `escape '\'` falhar silenciosamente no H2.
		return "%" + term.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
	}
}
