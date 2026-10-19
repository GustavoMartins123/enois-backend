package com.socialcoisa.rede.shared.security;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.socialcoisa.rede.profile.application.port.out.UserProfileRepository;
import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Impede contas recem-criadas de publicar imediatamente.
 *
 * Bots criam conta e postam em seguida (milissegundos). Humanos gastam tempo navegando,
 * configurando perfil e lendo o feed antes de postar. Um intervalo curto (30s por padrao) nao
 * incomoda ninguem de verdade, mas quebra o script automatizado.
 */
@Component
public class NewAccountCooldown {

	private final UserProfileRepository userProfileRepository;
	private final Clock clock;
	private final long cooldownSeconds;

	public NewAccountCooldown(UserProfileRepository userProfileRepository, Clock clock,
			@Value("${rede.security.new-account-cooldown-seconds:30}") long cooldownSeconds) {
		this.userProfileRepository = userProfileRepository;
		this.clock = clock;
		this.cooldownSeconds = cooldownSeconds;
	}

	/**
	 * Verifica se a conta e velha o suficiente para publicar. Lanca 429 se ainda no cooldown.
	 */
	public void require(String userId) {
		if (cooldownSeconds <= 0) return;
		userProfileRepository.findById(userId).ifPresent(profile -> {
			Duration age = Duration.between(profile.createdAt(), clock.instant());
			if (age.getSeconds() < cooldownSeconds) {
				throw new ApiException(429, "ACCOUNT_TOO_NEW",
					"Sua conta foi criada ha poucos segundos. Aguarde um momento antes de publicar.");
			}
		});
	}
}
