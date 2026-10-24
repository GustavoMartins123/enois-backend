package com.socialcoisa.rede.contributors.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.socialcoisa.rede.contributors.application.port.out.ContributorApplicationRepository;
import com.socialcoisa.rede.contributors.domain.ContributorApplication;
import com.socialcoisa.rede.contributors.domain.ContributorApplicationStatus;
import com.socialcoisa.rede.contributors.domain.ContributorExperience;
import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Fila de contribuidores do projeto open source.
 *
 * <p>A landing é pública, então a validação é conservadora: campos com teto, GitHub normalizado
 * para o handle e aceite dos termos obrigatório. O registro existe para documentar quem procurou o
 * projeto e em que ponto do processo cada pessoa está.</p>
 */
@Service
public class ContributorApplicationService {

	private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
	private static final Pattern GITHUB = Pattern.compile("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?$");
	private static final Set<String> AREAS = Set.of("ENGENHARIA", "DESIGN", "PRODUTO", "DADOS", "INFRA",
		"DOCUMENTACAO", "COMUNIDADE", "OUTRO");
	private static final int MAX_LIMIT = 200;

	private final ContributorApplicationRepository repository;
	private final Clock clock;

	public ContributorApplicationService(ContributorApplicationRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	@Transactional
	public ContributorApplication apply(ContributorApplicationCommand command) {
		if (command == null || !command.acceptedTerms()) {
			throw new ApiException(400, "TERMS_NOT_ACCEPTED",
				"Para contribuir, aceite as regras de contribuição e os termos de uso.");
		}
		String name = required(command.name(), 2, 120, "INVALID_NAME", "Informe seu nome (2 a 120 caracteres).");
		String github = github(command.github());
		String email = email(command.email());
		String area = area(command.area());
		ContributorExperience experience = experience(command.experience());
		String message = optional(command.message(), 2000, "INVALID_MESSAGE", "A mensagem passa de 2000 caracteres.");

		Instant now = clock.instant();
		ContributorApplication application = ContributorApplication.create(
			"contrib_" + UUID.randomUUID().toString().replace("-", ""), name, github, email, area, experience,
			message, now);
		return repository.save(application);
	}

	public List<ContributorApplication> list(ContributorApplicationStatus status, int limit) {
		return repository.list(status, Math.max(1, Math.min(limit, MAX_LIMIT)));
	}

	@Transactional
	public ContributorApplication updateStatus(String id, ContributorApplicationStatus status) {
		if (status == null) {
			throw new ApiException(400, "INVALID_STATUS", "Informe o novo status da candidatura.");
		}
		ContributorApplication application = repository.findById(id)
			.orElseThrow(() -> new ApiException(404, "CONTRIBUTOR_NOT_FOUND", "Candidatura não encontrada."));
		return repository.save(application.withStatus(status));
	}

	private static String required(String value, int min, int max, String code, String message) {
		String clean = clean(value);
		if (clean == null || clean.length() < min) {
			throw new ApiException(400, code, message);
		}
		if (clean.length() > max) {
			throw new ApiException(400, code, message);
		}
		return clean;
	}

	private static String optional(String value, int max, String code, String message) {
		String clean = clean(value);
		if (clean != null && clean.length() > max) {
			throw new ApiException(400, code, message);
		}
		return clean;
	}

	/** Aceita @handle, usuário solto ou a URL do GitHub; guarda sempre o handle em minúsculas. */
	private static String github(String value) {
		String raw = clean(value);
		if (raw == null) {
			throw new ApiException(400, "INVALID_GITHUB", "Informe seu usuário do GitHub.");
		}
		String handle = raw;
		int githubIndex = raw.toLowerCase(Locale.ROOT).indexOf("github.com/");
		if (githubIndex >= 0) {
			handle = raw.substring(githubIndex + "github.com/".length());
		}
		handle = handle.replaceFirst("^@", "").replaceAll("/+$", "");
		handle = handle.toLowerCase(Locale.ROOT);
		if (!GITHUB.matcher(handle).matches()) {
			throw new ApiException(400, "INVALID_GITHUB", "Usuário do GitHub inválido.");
		}
		return handle;
	}

	private static String email(String value) {
		String clean = clean(value);
		if (clean == null || clean.length() > 180 || !EMAIL.matcher(clean).matches()) {
			throw new ApiException(400, "INVALID_EMAIL", "Informe um e-mail válido.");
		}
		return clean.toLowerCase(Locale.ROOT);
	}

	private static String area(String value) {
		String clean = clean(value);
		if (clean == null) {
			return null;
		}
		String normalized = clean.toUpperCase(Locale.ROOT);
		if (!AREAS.contains(normalized)) {
			throw new ApiException(400, "INVALID_AREA", "Área de contribuição inválida.");
		}
		return normalized;
	}

	private static ContributorExperience experience(String value) {
		String clean = clean(value);
		if (clean == null) {
			return null;
		}
		try {
			return ContributorExperience.valueOf(clean.toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException invalid) {
			throw new ApiException(400, "INVALID_EXPERIENCE", "Experiência inválida.");
		}
	}

	private static String clean(String value) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		String clean = value.replaceAll("\\p{Cntrl}", "").trim();
		return clean.isEmpty() ? null : clean;
	}
}
