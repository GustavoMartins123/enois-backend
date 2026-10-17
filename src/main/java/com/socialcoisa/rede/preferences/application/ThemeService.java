package com.socialcoisa.rede.preferences.application;

import java.time.Clock;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.socialcoisa.rede.preferences.application.port.out.UserPreferenceRepository;
import com.socialcoisa.rede.preferences.domain.ThemeIds;
import com.socialcoisa.rede.preferences.domain.UserPreference;
import com.socialcoisa.rede.shared.web.ApiException;

/**
 * O tema da conta: escolha individual, igual para todo mundo.
 *
 * O backend valida o identificador contra a lista do front — tema desconhecido é
 * recusa (400), não silêncio — e guarda a escolha para ela acompanhar a pessoa
 * entre dispositivos. Limpar (`null`) volta ao padrão da marca.
 */
@Service
public class ThemeService {

	private final UserPreferenceRepository repository;
	private final Clock clock;

	public ThemeService(UserPreferenceRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	public Optional<String> themeOf(String userId) {
		return repository.find(userId).map(UserPreference::themeId);
	}

	public void setTheme(String userId, String themeId) {
		if (themeId == null || themeId.isBlank()) {
			repository.delete(userId);
			return;
		}
		String normalized = themeId.trim();
		if (!ThemeIds.ALL.contains(normalized)) {
			throw new ApiException(400, "INVALID_THEME", "Tema desconhecido: escolha um dos temas disponiveis.");
		}
		repository.save(new UserPreference(userId, normalized, clock.instant()));
	}
}
