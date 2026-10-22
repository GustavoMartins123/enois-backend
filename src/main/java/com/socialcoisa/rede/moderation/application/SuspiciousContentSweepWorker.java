package com.socialcoisa.rede.moderation.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.socialcoisa.rede.moderation.domain.SanctionType;
import com.socialcoisa.rede.profile.application.port.out.UserProfileRepository;
import com.socialcoisa.rede.shared.sanctions.SanctionGateReader;
import com.socialcoisa.rede.shared.web.AdminGuard;

/**
 * Varredura de CONTEÚDO suspeito em todos os módulos que aceitam dado público (comunidades,
 * grupos, eventos, vagas, blogs, freelas, empresas, mentorias, marketplace, serviços, projetos e
 * jogos), em vez de depender de um endpoint específico.
 *
 * A lista de tabelas/colunas e as assinaturas ficam em {@link SuspiciousContentTargets}
 * (configuráveis). Para cada linha recente que casa com uma assinatura de ataque:
 * 1. apaga a linha (a não ser em dry-run);
 * 2. junta o autor;
 * 3. ao fim, quem produziu conteúdo com assinatura de ataque é banido PERMANENTEMENTE pelo fluxo
 *    normal de sanção — que revoga sessões, arquiva posts/comentários e passa pelo
 *    {@link BanCleanupService}.
 *
 * Cuidados: nunca pune admin da allowlist; pula quem já está banido; só olha a janela recente;
 * não usa texto do usuário para montar SQL (tabela/colunas vêm da config e são validadas como
 * identificadores); falha de FK em uma linha não interrompe a varredura.
 */
@Component
public class SuspiciousContentSweepWorker {

	private static final Logger LOG = LoggerFactory.getLogger(SuspiciousContentSweepWorker.class);

	/** Ator da trilha: é a automação, não uma pessoa. */
	static final String ACTOR = "automod:varredura";
	/** Assinaturas que só existem em ataque (não aparecem em conversa legítima). */
	private static final java.util.Set<String> HARD_PATTERNS = java.util.Set.of("mass assignment",
		"quota probe", "idem probe", "probe zq", "msm125");

	private final JdbcTemplate jdbc;
	private final SuspiciousContentTargets config;
	private final ModeratorService moderators;
	private final UserProfileRepository profiles;
	private final SanctionGateReader sanctionGate;
	private final AdminGuard adminGuard;
	private final Clock clock;

	public SuspiciousContentSweepWorker(JdbcTemplate jdbc, SuspiciousContentTargets config,
			ModeratorService moderators, UserProfileRepository profiles, SanctionGateReader sanctionGate,
			AdminGuard adminGuard, Clock clock) {
		this.jdbc = jdbc;
		this.config = config;
		this.moderators = moderators;
		this.profiles = profiles;
		this.sanctionGate = sanctionGate;
		this.adminGuard = adminGuard;
		this.clock = clock;
	}

	@Scheduled(initialDelayString = "${rede.moderation.content-sweep.initial-delay-ms:20000}",
			fixedDelayString = "${rede.moderation.content-sweep.interval-ms:120000}")
	public void run() {
		if (!config.enabled()) {
			return;
		}
		try {
			sweep();
		}
		catch (RuntimeException failure) {
			LOG.error("content_sweep_failed", failure);
		}
	}

	/** Uma passada; devolve o relatório (também usado pelos testes e pelo log). */
	public Report sweep() {
		if (config.targets().isEmpty() || config.patterns().isEmpty()) {
			return new Report(0, 0, 0, 0, List.of());
		}
		Instant since = clock.instant().minus(Duration.ofHours(config.sinceHours()));
		Map<String, AuthorHits> authors = new LinkedHashMap<>();
		int found = 0;
		int deleted = 0;
		int deleteFailures = 0;

		for (SuspiciousContentTargets.Target target : config.targets()) {
			for (Hit hit : find(target, since)) {
				found++;
				LOG.warn("suspicious_content kind={} id={} author={} pattern={} dryRun={}", target.table(),
					hit.id(), hit.authorId(), hit.pattern(), config.dryRun());
				if (!config.dryRun()) {
					try {
						jdbc.update("DELETE FROM " + target.table() + " WHERE " + target.idColumn() + " = ?",
							hit.id());
						deleted++;
					}
					catch (DataAccessException failure) {
						// FK (comunidade com membros, vaga com candidaturas...): o conteúdo some da
						// listagem pelo ban do autor; a linha fica para limpeza manual.
						deleteFailures++;
						LOG.warn("content_sweep_delete_failed table={} id={} reason={}", target.table(), hit.id(),
							failure.getClass().getSimpleName());
					}
				}
				if (hit.authorId() != null) {
					authors.computeIfAbsent(hit.authorId(), key -> new AuthorHits()).add(target.table(), hit.pattern());
				}
			}
		}

		int banned = 0;
		int banFailures = 0;
		if (!config.dryRun()) {
			for (var entry : authors.entrySet()) {
				String authorId = entry.getKey();
				if (sanctionGate.banned(authorId)) {
					continue;
				}
				var profile = profiles.findById(authorId).orElse(null);
				if (profile == null) {
					continue;
				}
				if (adminGuard.isAdmin(profile.email(), profile.username())) {
					LOG.error("content_sweep_skip_admin username={} (assinatura de ataque, revise manualmente)",
						profile.username());
					continue;
				}
				boolean hard = entry.getValue().patterns().stream().anyMatch(HARD_PATTERNS::contains);
				// Equipe e conteúdo legítimo de segurança (que cita <script>/onerror) não podem ser
				// banidos por um único indício: a equipe só cai com assinatura de ataque, e as
				// assinaturas "moles" exigem mais de um item.
				if (moderators.isModerator(authorId) && !hard) {
					LOG.error("content_sweep_skip_moderator username={} (revise manualmente)", profile.username());
					continue;
				}
				if (!hard && entry.getValue().count() < 2) {
					LOG.warn("content_sweep_no_ban username={} hits={} (apagado, sem ban automático)",
						profile.username(), entry.getValue().count());
					continue;
				}
				String reason = "Varredura automática de conteúdo suspeito: " + entry.getValue().summary()
					+ ". Conteúdo apagado e conta banida permanentemente.";
				try {
					var result = moderators.sanctionUser(ACTOR, profile.username(), SanctionType.PERMABAN, reason);
					if (!"ALREADY_BANNED".equals(result.status())) {
						moderators.sanitizeUser(ACTOR, profile.username(), reason);
						banned++;
						LOG.warn("content_sweep_banned username={} hits={} patterns={}", profile.username(),
							entry.getValue().count(), entry.getValue().patterns());
					}
				}
				catch (RuntimeException failure) {
					banFailures++;
					LOG.warn("content_sweep_ban_failed username={} reason={}", profile.username(),
						failure.getClass().getSimpleName());
				}
			}
		}

		LOG.info("content_sweep done found={} deleted={} deleteFailures={} banned={} banFailures={} dryRun={}", found,
			deleted, deleteFailures, banned, banFailures, config.dryRun());
		return new Report(found, deleted, deleteFailures, banned, List.copyOf(authors.keySet()));
	}

	private List<Hit> find(SuspiciousContentTargets.Target target, Instant since) {
		StringBuilder sql = new StringBuilder("SELECT ").append(target.idColumn()).append(", ")
			.append(target.authorColumn());
		for (String text : target.textColumns()) {
			sql.append(", ").append(text);
		}
		sql.append(" FROM ").append(target.table()).append(" WHERE ").append(target.createdColumn())
			.append(" >= ? AND (");
		List<Object> params = new ArrayList<>();
		params.add(Timestamp.from(since));
		boolean first = true;
		for (String text : target.textColumns()) {
			for (String pattern : config.patterns()) {
				if (!first) {
					sql.append(" OR ");
				}
				first = false;
				sql.append("LOWER(COALESCE(").append(text).append(", '')) LIKE ?");
				params.add("%" + pattern + "%");
			}
		}
		sql.append(")");
		// Vaga externa importada não é conteúdo da comunidade: só as submetidas por alguém.
		if ("external_jobs".equals(target.table())) {
			sql.append(" AND submitted_by IS NOT NULL");
		}
		sql.append(" LIMIT ?");
		params.add(config.maxRowsPerTarget());

		return jdbc.query(sql.toString(), (rs, rowNum) -> {
			String id = rs.getString(1);
			String author = rs.getString(2);
			String matched = null;
			for (int column = 0; column < target.textColumns().size() && matched == null; column++) {
				String text = rs.getString(3 + column);
				if (text == null) {
					continue;
				}
				String lower = text.toLowerCase(Locale.ROOT);
				for (String pattern : config.patterns()) {
					if (lower.contains(pattern)) {
						matched = pattern;
						break;
					}
				}
			}
			return new Hit(id, author, matched == null ? "?" : matched);
		}, params.toArray());
	}

	private record Hit(String id, String authorId, String pattern) {
	}

	private static final class AuthorHits {
		private int count;
		private final java.util.Set<String> patterns = new java.util.LinkedHashSet<>();
		private final java.util.Set<String> tables = new java.util.LinkedHashSet<>();

		void add(String table, String pattern) {
			count++;
			tables.add(table);
			patterns.add(pattern);
		}

		int count() {
			return count;
		}

		java.util.Set<String> patterns() {
			return patterns;
		}

		String summary() {
			return count + " item(ns) em " + String.join(", ", tables) + " (" + String.join(", ", patterns) + ")";
		}
	}

	public record Report(int found, int deleted, int deleteFailures, int banned, List<String> authorIds) {
	}
}
