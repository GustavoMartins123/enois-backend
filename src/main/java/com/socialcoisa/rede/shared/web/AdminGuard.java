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
 * Restringe operacoes administrativas a uma allowlist de e-mails ({@code REDE_ADMIN_EMAILS}) e de
 * usernames ({@code REDE_ADMIN_USERNAMES}) separados por virgula. O papel de admin nao existe no
 * schema, entao a autorizacao e por configuracao.
 *
 * O username entra como alternativa para o painel de moderacao: as contas donas do projeto
 * (aminadojava e mrfoxxo) precisam de admin independentemente do e-mail cadastrado. Usernames
 * podem ser renomeados; por isso a allowlist de e-mail continua valendo e a de username e
 * configuravel no cluster.
 */
@Component
public class AdminGuard {

	private final UserProfileRepository profileRepository;
	private final Set<String> adminEmails;
	private final Set<String> adminUsernames;

	public AdminGuard(UserProfileRepository profileRepository,
			@Value("${rede.admin.emails:}") String adminEmails,
			@Value("${rede.admin.usernames:aminadojava,mrfoxxo}") String adminUsernames) {
		this.profileRepository = profileRepository;
		this.adminEmails = normalize(adminEmails);
		this.adminUsernames = normalize(adminUsernames);
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
		return isAdmin(profile.email(), profile.username());
	}

	/** Mesma allowlist, para identificadores já carregados (ex.: triagem de contas suspeitas). */
	public boolean isAdmin(String email, String username) {
		if (email != null && adminEmails.contains(email.toLowerCase(Locale.ROOT))) {
			return true;
		}
		return username != null && adminUsernames.contains(username.toLowerCase(Locale.ROOT));
	}
}