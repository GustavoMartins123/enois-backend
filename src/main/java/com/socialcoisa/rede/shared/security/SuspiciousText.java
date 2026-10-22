package com.socialcoisa.rede.shared.security;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Texto com o vocabulário do ataque em série (títulos/nomes de conteúdo, não só de conta).
 *
 * As vitrines de cada módulo usam isto para não exibir conteúdo criado com o padrão do ataque,
 * mesmo quando o autor não foi (ou ainda não foi) banido — foi o caso dos eventos "MSM...",
 * das comunidades "MSM125917 iso ..." e dos anúncios "Probe Escrow".
 */
@Component
public class SuspiciousText {

	private final List<String> patterns;

	public SuspiciousText(
			@Value("${rede.moderation.content-sweep.patterns:probe,idor,redteam,msm}") String patternList) {
		this.patterns = Arrays.stream(patternList.split(","))
			.map(String::trim)
			.filter(StringUtils::hasText)
			.map(pattern -> pattern.toLowerCase(Locale.ROOT))
			.toList();
	}

	/** Algum dos campos carrega o padrão? Nulo/vazio nunca casa. */
	public boolean matches(String... fields) {
		if (patterns.isEmpty() || fields == null) {
			return false;
		}
		for (String field : fields) {
			if (field == null || field.isBlank()) {
				continue;
			}
			String normalized = field.toLowerCase(Locale.ROOT);
			if (patterns.stream().anyMatch(normalized::contains)) {
				return true;
			}
		}
		return false;
	}
}
