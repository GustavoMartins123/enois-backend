package com.socialcoisa.rede.shared.web;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.socialcoisa.rede.profile.application.port.out.UserProfileRepository;
import com.socialcoisa.rede.profile.domain.UserProfile;

/**
 * Autoriza por IDs imutaveis ({@code REDE_ADMIN_USER_IDS}) ou e-mails verificados
 * ({@code REDE_ADMIN_EMAILS}), separados por virgula. Username editavel nunca concede poder.
 */
@Component
public class AdminGuard {

	private final UserProfileRepository profileRepository;
	private final Set<String> adminEmails;
	private final Set<String> adminUserIds;

	public AdminGuard(UserProfileRepository profileRepository,
			@Value("${rede.admin.emails:}") String adminEmails,
			@Value("${rede.admin.user-ids:}") String adminUserIds) {
		this.profileRepository = profileRepository;
		this.adminEmails = normalize(adminEmails);
		this.adminUserIds = Arrays.stream(adminUserIds.split(",")).map(String::trim)
			.filter(item -> !item.isEmpty()).collect(Collectors.toUnmodifiableSet());
	}

	private static Set<String> normalize(String value) {
		return Arrays.stream(value.split(","))
			.map(String::trim)
			.filter(item -> !item.isEmpty())
			.map(item -> item.toLowerCase(Locale.ROOT))
			.collect(Collectors.toUnmodifiableSet());
	}

	public void requireAdmin(CurrentUser user) {
		if (!isAdmin(user)) {
			throw new ApiException(403, "FORBIDDEN", "Acesso restrito a administradores.");
		}
	}

	/** Mesma regra do requireAdmin, mas devolve o resultado para a tela decidir o que mostrar. */
	public boolean isAdmin(CurrentUser user) {
		return profileRepository.findById(user.id().value())
			.map(this::isAdmin)
			.orElse(false);
	}

	private boolean isAdmin(UserProfile profile) {
		return adminUserIds.contains(profile.id()) || (profile.emailVerified() && profile.email() != null
			&& adminEmails.contains(profile.email().toLowerCase(Locale.ROOT)));
	}

	/** Mesma allowlist, para identificadores já carregados (ex.: triagem de contas suspeitas). */
	public boolean isAdmin(String email, String username) {
		// Username locates a profile; it never grants authority by itself.
		return username != null && profileRepository.findByUsername(username).map(this::isAdmin).orElse(false);
	}
}
