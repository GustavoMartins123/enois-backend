package com.socialcoisa.rede.preferences.application.port.out;

import java.util.Optional;

import com.socialcoisa.rede.preferences.domain.UserPreference;

/** Guarda as preferências que acompanham a conta (uma linha por usuário). */
public interface UserPreferenceRepository {

	Optional<UserPreference> find(String userId);

	void save(UserPreference preference);

	void delete(String userId);
}
