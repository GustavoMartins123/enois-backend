package com.socialcoisa.rede.jobs.application;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.socialcoisa.rede.jobs.application.port.out.ExternalJobRepository;
import com.socialcoisa.rede.jobs.domain.ExternalJob;
import com.socialcoisa.rede.jobs.domain.UrlCanonicalizer;
import com.socialcoisa.rede.profile.application.ReputationService;
import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Vagas externas cadastradas PELA COMUNIDADE: a pessoa cola a vaga de outro site e o enois so
 * lista, com o botao indo para o link de origem — a candidatura nunca passa por aqui.
 *
 * O registro vive na MESMA tabela das importadas (external_jobs), marcado com `submitted_by`
 * e a plataforma "Comunidade": assim a listagem, o filtro por plataforma, a busca e a dedupe por
 * URL canonica (que ja existem) valem para as duas origens sem codigo paralelo.
 */
@Service
public class ExternalJobSubmissionService {

	/** Aparece no selo "Vaga externa · Comunidade" e no filtro da aba. */
	public static final String COMMUNITY_PLATFORM = "Comunidade";

	private static final int MAX_TITLE = 300;
	private static final int MAX_COMPANY = 200;
	private static final int MAX_LOCATION = 200;
	private static final int MAX_URL = 1000;

	private final ExternalJobRepository repository;
	private final ReputationService reputationService;
	private final CacheManager cacheManager;
	private final Clock clock;

	public ExternalJobSubmissionService(ExternalJobRepository repository, ReputationService reputationService,
			CacheManager cacheManager, Clock clock) {
		this.repository = repository;
		this.reputationService = reputationService;
		this.cacheManager = cacheManager;
		this.clock = clock;
	}

	@Transactional
	public ExternalJob submit(String userId, String title, String company, String location, String url,
			boolean communityAgreement) {
		return submit(userId, title, company, location, url, null, null, null, null, null, communityAgreement);
	}

	/** Cadastro completo: detalhes da vaga e canal de candidatura opcionais. */
	@Transactional
	public ExternalJob submit(String userId, String title, String company, String location, String url,
			String description, String salaryMin, String salaryMax, String salaryPeriod, String applyEmail,
			boolean communityAgreement) {
		String cleanTitle = required(title, MAX_TITLE, "Informe o titulo da vaga.");
		String cleanCompany = required(company, MAX_COMPANY, "Informe a empresa da vaga.");
		String cleanLocation = optional(location, MAX_LOCATION);
		String cleanUrl = validateUrl(url);
		// Mesmo gate da vaga da casa: cadastrar vaga (de qualquer origem) e um ato de confianca.
		if (reputationService.reputationOf(userId) <= JobService.MIN_REPUTATION_TO_CREATE_JOB) {
			throw new ApiException(403, "LOW_REPUTATION", "E necessario ter mais de "
				+ JobService.MIN_REPUTATION_TO_CREATE_JOB + " de reputacao para cadastrar vagas.");
		}
		if (!communityAgreement) {
			throw new ApiException(400, "COMMUNITY_RULES_NOT_ACCEPTED",
				"E preciso declarar que a vaga e real e esta de acordo com as regras da comunidade.");
		}
		// A dedupe GLOBAL ja existe (mesma URL canonica = mesma vaga, ver UrlCanonicalizer): sem ela
		// a mesma vaga entraria duas vezes — uma importada e outra colada a mao.
		String canonicalUrl = UrlCanonicalizer.canonicalize(cleanUrl);
		if (canonicalUrl != null && repository.findByCanonicalUrl(canonicalUrl).isPresent()) {
			throw new ApiException(409, "JOB_ALREADY_LISTED", "Esta vaga já está listada no Énóis.");
		}
		String suffix = UUID.randomUUID().toString().replace("-", "");
		Instant now = clock.instant();
		ExternalJob job = new ExternalJob("ext_com_" + suffix, COMMUNITY_PLATFORM, "community_" + suffix,
			cleanTitle, cleanCompany, cleanLocation, null, null, cleanUrl, now, now, ExternalJob.FOUND,
			canonicalUrl, cleanApplyEmail(applyEmail), userId, optional(description, 5000),
			optional(salaryMin, 50), optional(salaryMax, 50), normalizePeriod(salaryPeriod));
		repository.save(job);
		evictListing();
		return job;
	}

	/** As vagas externas que EU cadastrei (a tela "Minhas vagas" mostra e permite excluir). */
	public List<ExternalJob> mine(String userId) {
		return repository.findBySubmitter(userId);
	}

	@Transactional
	public void remove(String userId, String id) {
		ExternalJob job = repository.findById(id)
			.orElseThrow(() -> new ApiException(404, "EXTERNAL_JOB_NOT_FOUND", "Vaga externa nao encontrada."));
		if (!userId.equals(job.submittedBy())) {
			throw new ApiException(403, "NOT_OWNER", "Voce nao pode excluir uma vaga que nao cadastrou.");
		}
		repository.delete(id);
		evictListing();
	}

	/** A listagem das externas tem cache (5 min): sem limpar, a vaga nova so apareceria depois. */
	private void evictListing() {
		Cache cache = cacheManager.getCache("externalJobs");
		if (cache != null) {
			cache.clear();
		}
	}

	private String required(String value, int max, String message) {
		if (!StringUtils.hasText(value)) {
			throw new ApiException(400, "INVALID_EXTERNAL_JOB", message);
		}
		String clean = value.trim().replaceAll("\\s+", " ");
		if (clean.length() > max) {
			throw new ApiException(400, "INVALID_EXTERNAL_JOB", message);
		}
		return clean;
	}

	/** E-mail/URL de candidatura informado pelo autor (opcional). */
	private String cleanApplyEmail(String value) {
		String clean = optional(value, 320);
		if (clean == null) {
			return null;
		}
		if (clean.toLowerCase(Locale.ROOT).startsWith("mailto:")) {
			clean = clean.substring("mailto:".length());
		}
		if (!clean.contains("@") || clean.startsWith("@") || clean.endsWith("@")) {
			throw new ApiException(400, "INVALID_APPLY_EMAIL", "Informe um e-mail de candidatura valido.");
		}
		return clean;
	}

	private String normalizePeriod(String value) {
		String clean = optional(value, 20);
		if (clean == null) {
			return null;
		}
		String upper = clean.toUpperCase(Locale.ROOT);
		return java.util.List.of("HOUR", "DAY", "MONTH", "YEAR").contains(upper) ? upper : null;
	}

	private String optional(String value, int max) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		String clean = value.trim().replaceAll("\\s+", " ");
		return clean.length() > max ? clean.substring(0, max) : clean;
	}

	/**
	 * O link e a unica coisa que sustenta a vaga externa (nao ha detalhe no enois): so HTTPS de
	 * site real passa. Esquema perigoso (`javascript:`, `data:`) e endereco com credenciais
	 * embutidas (`https://outro@malicioso`) sao recusados — o botao "Visitar vaga" abre isso direto.
	 */
	private String validateUrl(String url) {
		if (!StringUtils.hasText(url)) {
			throw new ApiException(400, "INVALID_EXTERNAL_JOB", "Informe o link da vaga.");
		}
		String clean = url.trim();
		if (clean.length() > MAX_URL) {
			throw new ApiException(400, "INVALID_EXTERNAL_JOB", "Link da vaga muito longo.");
		}
		URI uri;
		try {
			uri = URI.create(clean);
		}
		catch (RuntimeException invalid) {
			throw new ApiException(400, "INVALID_EXTERNAL_JOB", "Link da vaga invalido.");
		}
		String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
		if (!"https".equals(scheme) || !StringUtils.hasText(uri.getHost()) || uri.getUserInfo() != null) {
			throw new ApiException(400, "INVALID_EXTERNAL_JOB",
				"O link da vaga precisa ser um endereco https valido.");
		}
		return clean;
	}
}
