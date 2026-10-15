package com.socialcoisa.rede.jobs.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.socialcoisa.rede.jobs.application.port.out.JobRepository;
import com.socialcoisa.rede.jobs.domain.Job;
import com.socialcoisa.rede.profile.application.ReputationService;
import com.socialcoisa.rede.shared.web.ApiException;

@Service
public class JobService {

	public static final int MIN_REPUTATION_TO_CREATE_JOB = 150;

	/** Moedas aceitas na faixa salarial. BRL e o padrao. */
	public static final java.util.Set<String> SUPPORTED_CURRENCIES = java.util.Set.of("BRL", "USD", "EUR");

	private static final int MAX_SALARY_LENGTH = 120;

	/** Simbolos que o autor costuma digitar e que a exibicao remonta a partir da moeda. */
	private static final java.util.regex.Pattern CURRENCY_SYMBOLS = java.util.regex.Pattern
		.compile("(?i)(R\\$|US\\$|\\$|€)");

	private final JobRepository repository;
	private final ReputationService reputationService;
	private final Clock clock;

	public JobService(JobRepository repository, ReputationService reputationService, Clock clock) {
		this.repository = repository;
		this.reputationService = reputationService;
		this.clock = clock;
	}

	@Transactional
	public Job create(String title, String company, String logo, String location, String type, String level,
			String salary, String currency, List<String> skills, String userId, boolean communityAgreement) {
		validate(title, company, type, level);
		int reputation = reputationService.reputationOf(userId);
		if (reputation <= MIN_REPUTATION_TO_CREATE_JOB) {
			throw new ApiException(403, "LOW_REPUTATION", "E necessario ter mais de " + MIN_REPUTATION_TO_CREATE_JOB
				+ " de reputacao para criar vagas.");
		}
		// Publicar e afirmar que a vaga e real e respeita as regras da comunidade; guardamos o instante.
		if (!communityAgreement) {
			throw new ApiException(400, "COMMUNITY_RULES_NOT_ACCEPTED",
				"E preciso declarar que a vaga e real e esta de acordo com as regras da comunidade.");
		}
		String id = "job_" + UUID.randomUUID().toString().replace("-", "");
		Job job = Job.create(id, title, company, logo, location, type, level, normalizeSalary(salary),
			normalizeCurrency(currency), skills, userId, clock.instant(), clock.instant());
		repository.save(job);
		return job;
	}

	@Transactional
	public Job update(String jobId, String title, String company, String logo, String location, String type, String level,
			String salary, String currency, List<String> skills, String userId) {
		validate(title, company, type, level);
		Job existing = repository.findById(jobId)
			.orElseThrow(() -> new ApiException(404, "JOB_NOT_FOUND", "Vaga nao encontrada."));
		if (!existing.postedBy().equals(userId)) {
			throw new ApiException(403, "NOT_OWNER", "Voce nao pode editar uma vaga que nao criou.");
		}
		Job updated = Job.restore(jobId, title, company, logo, location, type, level, normalizeSalary(salary),
			normalizeCurrency(currency), skills, userId, existing.communityAgreedAt(), existing.postedAt(),
			clock.instant());
		repository.save(updated);
		return updated;
	}

	@Transactional
	public void delete(String jobId, String userId) {
		Job existing = repository.findById(jobId)
			.orElseThrow(() -> new ApiException(404, "JOB_NOT_FOUND", "Vaga nao encontrada."));
		if (!existing.postedBy().equals(userId)) {
			throw new ApiException(403, "NOT_OWNER", "Voce nao pode excluir uma vaga que nao criou.");
		}
		repository.deleteById(jobId);
	}

	@Transactional
	public void apply(String jobId, String userId) {
		repository.findById(jobId)
			.orElseThrow(() -> new ApiException(404, "JOB_NOT_FOUND", "Vaga nao encontrada."));
		if (repository.hasApplied(jobId, userId)) {
			throw new ApiException(409, "ALREADY_APPLIED", "Voce ja se candidatou a esta vaga.");
		}
		repository.saveApplication(jobId, userId);
	}

	@Transactional
	public void save(String jobId, String userId) {
		if (repository.hasBookmarked(jobId, userId)) {
			throw new ApiException(409, "ALREADY_SAVED", "Vaga ja salva.");
		}
		repository.saveBookmark(jobId, userId);
	}

	@Transactional
	public void removeSave(String jobId, String userId) {
		if (!repository.hasBookmarked(jobId, userId)) {
			throw new ApiException(404, "NOT_SAVED", "Vaga nao estava salva.");
		}
		repository.removeBookmark(jobId, userId);
	}

	public List<Job> list(String query, String type, String level, String location, List<String> skills,
			java.time.Instant before, int limit) {
		return repository.findAll(query, type, level, location, skills, before, limit);
	}

	public Job getById(String id) {
		return repository.findById(id)
			.orElseThrow(() -> new ApiException(404, "JOB_NOT_FOUND", "Vaga nao encontrada."));
	}

	/** Vagas publicadas pelo usuario (com quantas candidaturas receberam). */
	public List<Job> listMine(String userId, java.time.Instant before, int limit) {
		return repository.findByPostedBy(userId, before, limit);
	}

	/** Vagas em que o usuario se candidatou. */
	public List<Job> listApplications(String userId, java.time.Instant before, int limit) {
		return repository.findAppliedByUser(userId, before, limit);
	}

	public java.util.Set<String> appliedJobIds(String userId, java.util.List<String> jobIds) {
		return repository.findAppliedJobIds(userId, jobIds);
	}

	public java.util.Set<String> savedJobIds(String userId, java.util.List<String> jobIds) {
		return repository.findSavedJobIds(userId, jobIds);
	}

	public java.util.Map<String, Integer> applicationsCountByJobIds(java.util.List<String> jobIds) {
		return repository.countApplicationsByJobIds(jobIds);
	}

	public int applicationsCount(String jobId) {
		return repository.countApplications(jobId);
	}

	private void validate(String title, String company, String type, String level) {
		if (!StringUtils.hasText(title)) {
			throw new ApiException(400, "INVALID_JOB", "Titulo da vaga e obrigatorio.");
		}
		if (title.trim().length() > 255) {
			throw new ApiException(400, "INVALID_JOB", "Titulo da vaga deve ter no maximo 255 caracteres.");
		}
		if (!StringUtils.hasText(company)) {
			throw new ApiException(400, "INVALID_JOB", "Empresa e obrigatoria.");
		}
		if (company.trim().length() > 255) {
			throw new ApiException(400, "INVALID_JOB", "Empresa deve ter no maximo 255 caracteres.");
		}
		if (!StringUtils.hasText(type)) {
			throw new ApiException(400, "INVALID_JOB", "Tipo de contratacao e obrigatorio.");
		}
		if (!StringUtils.hasText(level)) {
			throw new ApiException(400, "INVALID_JOB", "Nivel da vaga e obrigatorio.");
		}
	}

	/**
	 * Deixa o valor da faixa salarial sem simbolo de moeda: o simbolo e escolhido no campo `currency`
	 * e remontado na exibicao, entao a mesma vaga nunca aparece como "R$ R$ 8.000" nem com dois
	 * simbolos diferentes.
	 */
	private static String normalizeSalary(String salary) {
		if (!StringUtils.hasText(salary)) {
			return null;
		}
		String cleaned = CURRENCY_SYMBOLS.matcher(salary).replaceAll(" ").replaceAll("\\s+", " ").trim();
		if (cleaned.isEmpty()) {
			return null;
		}
		return cleaned.length() > MAX_SALARY_LENGTH ? cleaned.substring(0, MAX_SALARY_LENGTH) : cleaned;
	}

	/** Moeda vigente da vaga: aceita apenas os codigos suportados; vazio vira BRL. */
	private static String normalizeCurrency(String currency) {
		if (!StringUtils.hasText(currency)) {
			return "BRL";
		}
		String code = currency.trim().toUpperCase(java.util.Locale.ROOT);
		if (!SUPPORTED_CURRENCIES.contains(code)) {
			throw new ApiException(400, "INVALID_JOB_CURRENCY",
				"Moeda nao suportada. Use BRL, USD ou EUR.");
		}
		return code;
	}

}
