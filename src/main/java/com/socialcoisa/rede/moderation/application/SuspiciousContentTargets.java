package com.socialcoisa.rede.moderation.application;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Onde a varredura de conteúdo suspeito olha. Cada alvo é uma tabela com o dono, o id, as colunas
 * de texto e a coluna de data — configurável para cobrir módulos novos sem código novo.
 *
 * Formato (separado por `;`): {@code tabela:coluna_dono:coluna_id:texto1|texto2:coluna_data}
 *
 * Os identificadores são validados como nomes de coluna/tabela; o SQL é montado só a partir
 * deles, nunca de texto do usuário.
 */
@Component
public class SuspiciousContentTargets {

	public record Target(String table, String authorColumn, String idColumn, List<String> textColumns,
			String createdColumn) {
	}

	private static final Pattern IDENTIFIER = Pattern.compile("[a-z_][a-z0-9_]*");

	private final boolean enabled;
	private final boolean dryRun;
	private final List<String> patterns;
	private final List<Target> targets;
	private final long sinceHours;
	private final int maxRowsPerTarget;

	public SuspiciousContentTargets(
			@Value("${rede.moderation.content-sweep.enabled:true}") boolean enabled,
			@Value("${rede.moderation.content-sweep.dry-run:false}") boolean dryRun,
			@Value("${rede.moderation.content-sweep.patterns:}") String patterns,
			@Value("${rede.moderation.content-sweep.targets:}") String targets,
			@Value("${rede.moderation.content-sweep.since-hours:48}") long sinceHours,
			@Value("${rede.moderation.content-sweep.max-rows-per-target:50}") int maxRowsPerTarget) {
		this.enabled = enabled;
		this.dryRun = dryRun;
		this.patterns = parsePatterns(patterns);
		this.targets = parseTargets(targets);
		this.sinceHours = Math.max(1, Math.min(sinceHours, 24 * 30));
		this.maxRowsPerTarget = Math.max(1, Math.min(maxRowsPerTarget, 500));
	}

	public boolean enabled() {
		return enabled;
	}

	public boolean dryRun() {
		return dryRun;
	}

	public List<String> patterns() {
		return patterns;
	}

	public List<Target> targets() {
		return targets;
	}

	public long sinceHours() {
		return sinceHours;
	}

	public int maxRowsPerTarget() {
		return maxRowsPerTarget;
	}

	static List<String> parsePatterns(String raw) {
		if (!StringUtils.hasText(raw)) {
			return List.of();
		}
		List<String> parsed = new ArrayList<>();
		for (String pattern : raw.split(",")) {
			String clean = pattern.trim().toLowerCase(java.util.Locale.ROOT);
			if (!clean.isEmpty()) {
				parsed.add(clean);
			}
		}
		return List.copyOf(parsed);
	}

	static List<Target> parseTargets(String raw) {
		if (!StringUtils.hasText(raw)) {
			return List.of();
		}
		List<Target> parsed = new ArrayList<>();
		for (String entry : raw.split(";")) {
			String[] parts = entry.trim().split(":");
			if (parts.length != 5) {
				continue;
			}
			String table = parts[0].trim();
			String author = parts[1].trim();
			String id = parts[2].trim();
			String created = parts[4].trim();
			List<String> texts = new ArrayList<>();
			for (String text : parts[3].split("\\|")) {
				String clean = text.trim();
				if (IDENTIFIER.matcher(clean).matches()) {
					texts.add(clean);
				}
			}
			if (texts.isEmpty() || !IDENTIFIER.matcher(table).matches() || !IDENTIFIER.matcher(author).matches()
					|| !IDENTIFIER.matcher(id).matches() || !IDENTIFIER.matcher(created).matches()) {
				continue;
			}
			parsed.add(new Target(table, author, id, List.copyOf(texts), created));
		}
		return List.copyOf(parsed);
	}
}
