package com.socialcoisa.rede.preferences.adapters.in.web;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.socialcoisa.rede.preferences.application.ThemeService;
import com.socialcoisa.rede.shared.web.CurrentUser;
import com.socialcoisa.rede.shared.web.CurrentUserResolver;

/**
 * Tema da conta: leitura e troca da preferência que acompanha a pessoa entre
 * dispositivos. O tema em si é decisão do front — aqui só existe o identificador
 * validado e o par de endpoints. Sem sessão, nada acontece (o convidado tem
 * preferência só local, que nunca passa por aqui).
 */
@RestController
class ThemeController {

	private final ThemeService themeService;
	private final CurrentUserResolver currentUserResolver;

	ThemeController(ThemeService themeService, CurrentUserResolver currentUserResolver) {
		this.themeService = themeService;
		this.currentUserResolver = currentUserResolver;
	}

	@GetMapping(value = "/me/theme", produces = MediaType.APPLICATION_JSON_VALUE)
	ThemeResponse theme(@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		return new ThemeResponse(themeService.themeOf(user.id().value()).orElse(null));
	}

	@PutMapping("/me/theme")
	ResponseEntity<Void> setTheme(@RequestHeader(value = "X-User-Id", required = false) String userId,
			@RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
			@RequestBody SetThemeRequest request) {
		CurrentUser user = currentUserResolver.resolve(userId, sessionToken);
		themeService.setTheme(user.id().value(), request.theme());
		return ResponseEntity.noContent().build();
	}

	/** `theme` ausente ou vazio limpa a preferência — volta ao padrão da marca. */
	private record ThemeResponse(String theme) {
	}
}
