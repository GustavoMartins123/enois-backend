package com.socialcoisa.rede.shared.web;

import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.socialcoisa.rede.profile.application.port.out.UserProfileRepository;
import com.socialcoisa.rede.profile.domain.UserProfile;

/**
 * Autoriza administração pelo papel IMUTÁVEL gravado em {@code users.is_admin} (V170).
 *
 * Não usa username nem e-mail: ambos são editáveis pelo usuário e a allowlist por esses campos
 * permitia escalação (EN-01/E-01). A promoção a admin é uma operação de banco, não um cadastro.
 * Os usernames históricos da equipe ficam RESERVADOS para ninguém assumir o nome depois.
 */
@Component
public class AdminGuard {

	/** Nomes históricos da equipe: ninguém consegue cadastrar/renomear para eles. */
	public static final Set<String> RESERVED_USERNAMES = Set.of("aminadojava", "mrfoxxo");

	private final UserProfileRepository profileRepository;
	/** SOMENTE TESTES: allowlist de e-mail ligada por `rede.admin.bootstrap-enabled` (default false). */
	private final Set<String> bootstrapEmails;

	public AdminGuard(UserProfileRepository profileRepository,
			@org.springframework.beans.factory.annotation.Value("${rede.admin.bootstrap-enabled:false}") boolean bootstrap,
			@org.springframework.beans.factory.annotation.Value("${rede.admin.emails:}") String adminEmails) {
		this.profileRepository = profileRepository;
		this.bootstrapEmails = bootstrap ? java.util.Arrays.stream(adminEmails.split(","))
			.map(String::trim)
			.filter(item -> !item.isEmpty())
			.map(item -> item.toLowerCase(Locale.ROOT))
			.collect(java.util.stream.Collectors.toUnmodifiableSet()) : Set.of();
	}

	public void requireAdmin(CurrentUser user) {
		if (!isAdmin(user)) {
			throw new ApiException(403, "FORBIDDEN", "Acesso restrito a administradores.");
		}
	}

	/** Mesma regra do requireAdmin, mas devolve o resultado para a tela decidir o que mostrar. */
	public boolean isAdmin(CurrentUser user) {
		return profileRepository.findById(user.id().value())
			.map(this::isAdminProfile)
			.orElse(false);
	}

	/** Para quem já carregou o perfil (triagem, varredura): não recarrega do banco. */
	public boolean isAdminProfile(UserProfile profile) {
		if (profile.admin()) {
			return true;
		}
		return profile.email() != null && bootstrapEmails.contains(profile.email().toLowerCase(Locale.ROOT));
	}

	/** Para projeções que só trazem o id (fila de suspeitos). */
	public boolean isAdminId(String userId) {
		return profileRepository.findById(userId).map(this::isAdminProfile).orElse(false);
	}

	public static boolean isReservedUsername(String username) {
		return username != null && RESERVED_USERNAMES.contains(username.trim().toLowerCase(Locale.ROOT));
	}
}
