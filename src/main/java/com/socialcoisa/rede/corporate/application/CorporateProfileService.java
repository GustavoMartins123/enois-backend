package com.socialcoisa.rede.corporate.application;

import java.time.Clock;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.socialcoisa.rede.corporate.application.port.out.CorporateProfileRepository;
import com.socialcoisa.rede.corporate.domain.CorporateProfile;
import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Serviço do perfil profissional (o alterego corporativo da conta).
 *
 * Regras que o domínio já garante e este serviço orquestra:
 * - o perfil nasce por iniciativa do usuário, SEMPRE em rascunho e SEM NADA copiado do perfil
 *   social (a importação com revisão é um fluxo separado);
 * - o @ profissional vive em namespace próprio, com unicidade no banco (o repositório traduce
 *   violação em 409);
 * - ativar exige os campos mínimos preenchidos (nome e headline) para que um perfil vazio ou
 *   incompleto nunca apareça como pronto;
 * - desativar não apaga nada.
 */
@Service
public class CorporateProfileService {

	private final CorporateProfileRepository repository;
	private final Clock clock;

	public CorporateProfileService(CorporateProfileRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	/** Cria o perfil do usuário (1:1: um por conta). Idempotente: já existe, devolve o atual. */
	public CorporateProfile create(String userId, String username, String name) {
		if (repository.findByUserId(userId).isPresent()) {
			throw new ApiException(409, "CORPORATE_PROFILE_EXISTS",
				"Você já tem um perfil profissional. Edite-o em vez de criar outro.");
		}
		if (repository.existsByUsername(normalize(username))) {
			throw new ApiException(409, "USERNAME_TAKEN",
				"Esse usuário profissional já está em uso. Escolha outro.");
		}
		return repository.save(CorporateProfile.create(userId, username, name, clock.instant()));
	}

	public Optional<CorporateProfile> byUser(String userId) {
		return repository.findByUserId(userId);
	}

	public Optional<CorporateProfile> byUsername(String username) {
		return repository.findByUsername(normalize(username));
	}

	public CorporateProfile updateDetails(String userId, String headline, String bio, String avatar,
			String banner, String location, String industry, String externalLinks) {
		CorporateProfile current = byUserIdOrThrow(userId);
		return repository.save(current.withDetails(
			trimOrNull(headline, 200), trimOrNull(bio, 2000), trimOrNull(avatar, 500), trimOrNull(banner, 500),
			trimOrNull(location, 200), trimOrNull(industry, 200), trimOrNull(externalLinks, 1000),
			clock.instant()));
	}

	public CorporateProfile rename(String userId, String name) {
		CorporateProfile current = byUserIdOrThrow(userId);
		return repository.save(current.withName(name, clock.instant()));
	}

	/** Ativação exige nome e headline: perfil incompleto nunca aparece como pronto. */
	public CorporateProfile activate(String userId) {
		CorporateProfile current = byUserIdOrThrow(userId);
		if (!StringUtils.hasText(current.headline())) {
			throw new ApiException(409, "PROFILE_INCOMPLETE",
				"Preencha a headline do perfil profissional antes de ativá-lo.");
		}
		return repository.save(current.activate(clock.instant()));
	}

	public CorporateProfile deactivate(String userId) {
		return repository.save(byUserIdOrThrow(userId).disable(clock.instant()));
	}

	private CorporateProfile byUserIdOrThrow(String userId) {
		return repository.findByUserId(userId)
			.orElseThrow(() -> new ApiException(404, "NO_BUSINESS_PROFILE",
				"Você ainda não tem um perfil profissional. Crie um para usar os recursos de trabalho."));
	}

	private String normalize(String username) {
		return username == null ? "" : username.trim().toLowerCase();
	}

	private String trimOrNull(String value, int max) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		String trimmed = value.trim();
		if (trimmed.length() > max) {
			throw new ApiException(400, "FIELD_TOO_LONG", "Um dos campos passou do limite de " + max + " caracteres.");
		}
		return trimmed;
	}
}
