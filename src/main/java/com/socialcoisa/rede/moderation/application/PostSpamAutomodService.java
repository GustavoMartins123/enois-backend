package com.socialcoisa.rede.moderation.application;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.socialcoisa.rede.feed.application.port.in.ModerationRemovePostUseCase;
import com.socialcoisa.rede.feed.application.port.out.PostRepository;
import com.socialcoisa.rede.feed.domain.Post;
import com.socialcoisa.rede.feed.domain.UserId;
import com.socialcoisa.rede.identity.application.port.out.AuthSessionRepository;
import com.socialcoisa.rede.moderation.ai.ModerationAiService;
import com.socialcoisa.rede.moderation.domain.UserSanction;
import com.socialcoisa.rede.shared.web.AdminGuard;
import com.socialcoisa.rede.shared.web.CurrentUser;

import tools.jackson.databind.JsonNode;

/**
 * Automod de SPAM/FLOOD — a moderação que NÃO depende de denúncia.
 *
 * O gatilho é determinístico e barato (contagens no próprio banco): muitos posts numa janela
 * curta (flood), o mesmo texto repetido, ou uma sequência de posts praticamente vazios. O
 * determinístico só LEVANTA A SUSPEITA — quem decide é a IA, com o mesmo veredito estruturado e
 * o mesmo gate da triagem de denúncias (PENALIZAR + sem ambiguidade + confiança ≥ 0.7). Sem esse
 * veredito, NADA é apagado nem aplicado: posts curtos e repetidos não são infração por si só
 * (a diretriz da casa diz isso), e um falso positivo aqui apaga conteúdo de gente honesta.
 *
 * Quando a IA confirma: apaga (remoção de moderação) os posts que a evidência aponta — inclusive
 * os ANTERIORES, não só o que disparou o gatilho — e aplica a sanção: RESTRIÇÃO com recurso no
 * primeiro caso, e BAN quando o flood é massivo (≥ auto-ban-threshold na janela), que é o caso de
 * conta de spam pura. Equipe (admin/moderador) fica fora do automod de propósito.
 */
@Service
public class PostSpamAutomodService {

	private static final Logger LOG = LoggerFactory.getLogger(PostSpamAutomodService.class);

	/** Id de moderação sintético que fica no histórico do post removido. */
	public static final String SYSTEM_MODERATOR = "automod-spam";

	/** Quantos posts recentes do autor entram na conta (teto de leitura). */
	private static final int SCAN_LIMIT = 100;

	/** Quantos posts viram amostra para a IA (o resto entra só nas contagens). */
	private static final int AI_SAMPLE = 12;

	/** Corte do texto de cada post na amostra: o modelo precisa do padrão, não do texto inteiro. */
	private static final int AI_SAMPLE_TEXT = 400;

	private final PostRepository posts;
	private final ModerationAiService ai;
	private final ModerationRemovePostUseCase removePost;
	private final SanctionService sanctions;
	private final AuthSessionRepository sessions;
	private final AdminGuard adminGuard;
	private final ModeratorService moderators;
	private final Clock clock;

	private final boolean enabled;
	private final int minLength;
	private final int floodWindowMinutes;
	private final int floodThreshold;
	private final int repeatThreshold;
	private final int shortFloodThreshold;
	private final int lookbackHours;
	private final int sanitizeLimit;
	private final int autoBanThreshold;

	/**
	 * Última avaliação por autor (memória do processo). Um flood de 50 posts NÃO pode enfileirar
	 * 50 análises com IA na mesma thread: avalia no máximo a cada `min-interval-minutes` por pessoa.
	 */
	private final java.util.concurrent.ConcurrentHashMap<String, Instant> ultimaAvaliacao =
		new java.util.concurrent.ConcurrentHashMap<>();
	private static final int MAX_THROTTLE_ENTRIES = 10_000;
	private final int minIntervalMinutes;

	public PostSpamAutomodService(PostRepository posts, ModerationAiService ai,
			ModerationRemovePostUseCase removePost, SanctionService sanctions, AuthSessionRepository sessions,
			AdminGuard adminGuard, ModeratorService moderators, Clock clock,
			@Value("${rede.moderation.spam.enabled:true}") boolean enabled,
			@Value("${rede.moderation.spam.min-length:3}") int minLength,
			@Value("${rede.moderation.spam.flood-window-minutes:10}") int floodWindowMinutes,
			@Value("${rede.moderation.spam.flood-threshold:8}") int floodThreshold,
			@Value("${rede.moderation.spam.repeat-threshold:3}") int repeatThreshold,
			@Value("${rede.moderation.spam.short-flood-threshold:8}") int shortFloodThreshold,
			@Value("${rede.moderation.spam.lookback-hours:168}") int lookbackHours,
			@Value("${rede.moderation.spam.sanitize-limit:50}") int sanitizeLimit,
			@Value("${rede.moderation.spam.auto-ban-threshold:20}") int autoBanThreshold,
			@Value("${rede.moderation.spam.min-interval-minutes:2}") int minIntervalMinutes) {
		this.posts = posts;
		this.ai = ai;
		this.removePost = removePost;
		this.sanctions = sanctions;
		this.sessions = sessions;
		this.adminGuard = adminGuard;
		this.moderators = moderators;
		this.clock = clock;
		this.enabled = enabled;
		this.minLength = Math.max(1, minLength);
		this.floodWindowMinutes = Math.max(1, floodWindowMinutes);
		this.floodThreshold = Math.max(2, floodThreshold);
		this.repeatThreshold = Math.max(2, repeatThreshold);
		this.shortFloodThreshold = Math.max(2, shortFloodThreshold);
		this.lookbackHours = Math.max(1, lookbackHours);
		this.sanitizeLimit = Math.max(1, sanitizeLimit);
		this.autoBanThreshold = Math.max(this.floodThreshold, autoBanThreshold);
		this.minIntervalMinutes = Math.max(0, minIntervalMinutes);
	}

	/** Resultado da avaliação (para log/teste; o fluxo real só age no PENALIZED). */
	public record Outcome(String status, int removed, String sanction) {
	}

	/**
	 * Avalia o autor de um post recém-publicado. Devolve o que aconteceu; nunca lança para o
	 * chamador (a publicação já está no ar e não pode falhar por causa do automod).
	 */
	@Transactional
	public Outcome evaluate(String userId) {
		if (!enabled) {
			return new Outcome("DISABLED", 0, null);
		}
		// Equipe fora: um falso positivo banindo quem cuida da rede seria pior do que o spam.
		if (adminGuard.isAdmin(new CurrentUser(new UserId(userId))) || moderators.isModerator(userId)) {
			return new Outcome("STAFF_SKIPPED", 0, null);
		}

		Instant now = clock.instant();

		// Conta já sancionada (restrita/banida) não é reanalisada: a sanção está de pé e a
		// restrição já impede publicar de novo.
		if (sanctions.activeSanctionOf(userId).isPresent()) {
			return new Outcome("ALREADY_SANCTIONED", 0, null);
		}

		Instant since = now.minus(lookbackHours, ChronoUnit.HOURS);
		Instant windowStart = now.minus(floodWindowMinutes, ChronoUnit.MINUTES);

		List<Post> recent = posts.findActiveByAuthor(new UserId(userId), now, SCAN_LIMIT).stream()
			.filter(post -> post.createdAt() != null && post.createdAt().isAfter(since))
			.toList();
		if (recent.isEmpty()) {
			return new Outcome("NO_SIGNAL", 0, null);
		}

		List<Post> naJanela = recent.stream()
			.filter(post -> post.createdAt().isAfter(windowStart))
			.toList();
		List<Post> curtasNaJanela = naJanela.stream().filter(this::isSuspiciouslyShort).toList();
		Map<String, List<Post>> iguais = gruposIguais(recent);
		int maiorGrupo = iguais.values().stream().mapToInt(List::size).max().orElse(0);

		boolean flood = naJanela.size() >= floodThreshold;
		boolean repeticao = maiorGrupo >= repeatThreshold;
		boolean floodCurto = curtasNaJanela.size() >= shortFloodThreshold;
		if (!flood && !repeticao && !floodCurto) {
			return new Outcome("NO_SIGNAL", 0, null);
		}
		// Only an actual signal consumes the AI interval. Reserve it atomically per author.
		if (ultimaAvaliacao.size() > MAX_THROTTLE_ENTRIES) {
			ultimaAvaliacao.entrySet().removeIf(entry ->
				!entry.getValue().plusSeconds(minIntervalMinutes * 60L).isAfter(now));
		}
		var acquired = new java.util.concurrent.atomic.AtomicBoolean();
		ultimaAvaliacao.compute(userId, (id, last) -> {
			if (last != null && last.plusSeconds(minIntervalMinutes * 60L).isAfter(now)) return last;
			acquired.set(true);
			return now;
		});
		if (!acquired.get()) return new Outcome("THROTTLED", 0, null);

		// Candidatos = o que a evidência aponta. Inclui os ANTERIORES (busca retroativa dentro da
		// janela de análise), não só o post que disparou o gatilho.
		Set<Post> candidatos = new LinkedHashSet<>();
		if (flood) {
			candidatos.addAll(naJanela);
		}
		if (repeticao) {
			iguais.values().stream().filter(grupo -> grupo.size() >= repeatThreshold).forEach(candidatos::addAll);
		}
		if (floodCurto) {
			candidatos.addAll(curtasNaJanela);
		}
		List<Post> alvo = candidatos.stream().limit(sanitizeLimit).toList();

		// A IA decide. Sem veredito válido (fora do ar/inválido) NADA é apagado.
		String amostra = amostra(alvo);
		String evidencia = ("Automod (sem denúncia): o mesmo autor publicou %d posts em %d minutos; "
			+ "%d posts idênticos na janela de análise e %d posts praticamente vazios. "
			+ "Verifique se é spam/flood ou uso legítimo da rede.").formatted(
			naJanela.size(), floodWindowMinutes, maiorGrupo, curtasNaJanela.size());
		var veredito = ai.triage(amostra, "SPAM", evidencia).flatMap(ai::parseVerdict);
		if (veredito.isEmpty() || !"PENALIZAR".equals(veredito.get().path("acao").asString())
				|| !ModerationAiService.canAutoPenalize(veredito.get())) {
			LOG.info("automod_spam_sem_confirmacao user={} flood={} repetidos={} curtos={}",
				userId, naJanela.size(), maiorGrupo, curtasNaJanela.size());
			return new Outcome("NEEDS_REVIEW", 0, null);
		}

		String motivo = motivo(veredito.get());

		int removidos = 0;
		for (Post post : alvo) {
			removePost.removeAsModerator(SYSTEM_MODERATOR, post.id().value(), motivo);
			removidos++;
		}
		boolean massivo = naJanela.size() >= autoBanThreshold;
		UserSanction sancao = sanctions.restrictUser(userId, "automod_spam_" + now.toEpochMilli(), motivo);
		if (massivo) {
			sanctions.banAuthor(sancao, userId, SYSTEM_MODERATOR, sessions);
		}
		LOG.warn("automod_spam_penalizado user={} removidos={} ban={}", userId, removidos, massivo);
		return new Outcome("PENALIZED", removidos, massivo ? "BAN" : "RESTRICTION");
	}

	/** Texto normalizado para achar repetição: sem caixa, sem espaços duplicados. */
	private Map<String, List<Post>> gruposIguais(List<Post> posts) {
		Map<String, List<Post>> grupos = new LinkedHashMap<>();
		for (Post post : posts) {
			String texto = normalizar(post.content());
			if (texto.isEmpty() || texto.length() > 1_000) {
				continue;
			}
			grupos.computeIfAbsent(texto, key -> new ArrayList<>()).add(post);
		}
		return grupos;
	}

	private String normalizar(String content) {
		if (!StringUtils.hasText(content)) {
			return "";
		}
		return content.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}

	/**
	 * "Quase vazio": texto abaixo do mínimo E sem mídia. Só conta como sinal em conjunto (uma
	 * sequência deles), nunca um post isolado.
	 */
	private boolean isSuspiciouslyShort(Post post) {
		if (post.mediaIds() != null && !post.mediaIds().isEmpty()) {
			return false;
		}
		String texto = normalizar(post.content());
		return !texto.isEmpty() && texto.length() < minLength;
	}

	private String amostra(List<Post> alvo) {
		StringBuilder amostra = new StringBuilder();
		for (Post post : alvo.stream().limit(AI_SAMPLE).toList()) {
			String texto = post.content() == null ? "" : post.content().trim();
			if (texto.length() > AI_SAMPLE_TEXT) {
				texto = texto.substring(0, AI_SAMPLE_TEXT) + "…";
			}
			if (amostra.length() > 0) {
				amostra.append("\n---\n");
			}
			amostra.append("[").append(post.createdAt()).append("] ").append(texto);
			if (post.mediaIds() != null && !post.mediaIds().isEmpty()) {
				amostra.append(" (com mídia)");
			}
		}
		return amostra.toString();
	}

	private String motivo(JsonNode veredito) {
		String justificativa = veredito.path("justificativa").asString();
		if (StringUtils.hasText(justificativa)) {
			return justificativa.length() > 300 ? justificativa.substring(0, 300) : justificativa;
		}
		String categoria = veredito.path("categoria").asString();
		return StringUtils.hasText(categoria) ? categoria : "flood/spam detectado automaticamente";
	}
}
