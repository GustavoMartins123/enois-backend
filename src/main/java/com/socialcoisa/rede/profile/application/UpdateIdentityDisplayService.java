package com.socialcoisa.rede.profile.application;

import java.time.Clock;

import org.springframework.stereotype.Service;

import com.socialcoisa.rede.profile.application.port.out.UserProfileRepository;
import com.socialcoisa.rede.profile.domain.UserProfile;
import com.socialcoisa.rede.shared.text.PlainText;
import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Identidade exibida: apelido, nome social e pronomes.
 *
 * Regras do produto: o nome social substitui o nome de cadastro para todo mundo (o
 * de cadastro fica para o dono da conta e para a empresa que recebe a candidatura);
 * o apelido entra entre parenteses depois do nome; os pronomes aparecem so no
 * perfil. Valor nulo/vazio LIMPA o campo — "nao quero mais" e uma intencao.
 */
@Service
public class UpdateIdentityDisplayService {

	private final UserProfileRepository repository;
	private final Clock clock;

	public UpdateIdentityDisplayService(UserProfileRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	public UserProfile update(String userId, String nickname, String socialName, String pronouns) {
		UserProfile profile = repository.findById(userId)
			.orElseThrow(() -> new ApiException(404, "PROFILE_NOT_FOUND", "Perfil nao encontrado."));
		check("apelido", nickname, 60);
		check("nome social", socialName, 160);
		check("pronomes", pronouns, 60);
		profile.updateIdentityDisplay(PlainText.defuse(nickname), PlainText.defuse(socialName),
			PlainText.defuse(pronouns), clock.instant());
		return repository.save(profile);
	}

	private void check(String field, String value, int max) {
		if (value != null && value.trim().length() > max) {
			throw new ApiException(400, "FIELD_TOO_LONG", field + " deve ter no maximo " + max + " caracteres.");
		}
	}
}
