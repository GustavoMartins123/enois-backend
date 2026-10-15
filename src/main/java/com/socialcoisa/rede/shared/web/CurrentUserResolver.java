package com.socialcoisa.rede.shared.web;

import java.time.Clock;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.socialcoisa.rede.feed.domain.UserId;
import com.socialcoisa.rede.identity.application.port.out.AuthSessionRepository;
import com.socialcoisa.rede.profile.application.port.out.UserProfileRepository;

@Component
public class CurrentUserResolver {

	private final AuthSessionRepository authSessionRepository;
	private final UserProfileRepository userProfileRepository;
	private final Clock clock;

	public CurrentUserResolver(AuthSessionRepository authSessionRepository, UserProfileRepository userProfileRepository,
			Clock clock) {
		this.authSessionRepository = authSessionRepository;
		this.userProfileRepository = userProfileRepository;
		this.clock = clock;
	}

	public CurrentUser resolve(String sessionToken) {
		if (!StringUtils.hasText(sessionToken)) {
			throw new ApiException(401, "UNAUTHENTICATED", "Sessao invalida ou expirada.");
		}
		String resolvedUserId = authSessionRepository.findActiveUserId(sessionToken.trim(), clock.instant())
			.orElseThrow(() -> new ApiException(401, "UNAUTHENTICATED", "Sessao invalida ou expirada."));
		return requireVerifiedEmail(new CurrentUser(new UserId(resolvedUserId)));
	}

	/**
	 * Conta pendente de confirmacao de e-mail nao usa as funcionalidades protegidas. O cadastro nao
	 * emite sessao, entao esta e a segunda barreira: pega uma sessao criada por outro caminho que
	 * ainda nao tenha passado pela confirmacao.
	 */
	private CurrentUser requireVerifiedEmail(CurrentUser user) {
		userProfileRepository.findById(user.id().value()).ifPresent(profile -> {
			if (!profile.emailVerified()) {
				throw new ApiException(403, "EMAIL_NOT_VERIFIED", "Confirme seu e-mail para usar a plataforma.");
			}
		});
		return user;
	}

	public CurrentUser resolve(String userId, String sessionToken) {
		if (StringUtils.hasText(sessionToken)) {
			return resolve(sessionToken);
		}
		throw new ApiException(401, "UNAUTHENTICATED", "Sessao invalida ou expirada.");
	}

	public CurrentUser resolveById(String userId) {
		if (!StringUtils.hasText(userId)) {
			throw new ApiException(401, "UNAUTHENTICATED", "Usuario autenticado ausente.");
		}
		return new CurrentUser(new UserId(userId.trim()));
	}
}
