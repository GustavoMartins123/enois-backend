package com.socialcoisa.rede.profile.adapters.out.persistence;

import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.stereotype.Component;

import com.socialcoisa.rede.profile.application.port.out.UserProfileRepository;
import com.socialcoisa.rede.shared.age.AgePolicyReader;

/**
 * Implementa a política de idade com o estado real do perfil. A idade é calculada no fuso de
 * São Paulo (o público é brasileiro; a virada de aniversário não pode depender do fuso do
 * servidor).
 */
@Component
class AgePolicyAdapter implements AgePolicyReader {

	private static final ZoneId BRAZIL = ZoneId.of("America/Sao_Paulo");

	private final UserProfileRepository profiles;

	AgePolicyAdapter(UserProfileRepository profiles) {
		this.profiles = profiles;
	}

	@Override
	public boolean birthDateDeclared(String userId) {
		return profiles.findById(userId).map(profile -> profile.birthDateDeclared()).orElse(false);
	}

	@Override
	public boolean isMinor(String userId) {
		LocalDate today = LocalDate.now(BRAZIL);
		return profiles.findById(userId).map(profile -> profile.isMinorOn(today)).orElse(false);
	}
}
