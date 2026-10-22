package com.socialcoisa.rede.moderation.application;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.socialcoisa.rede.shared.security.SignupAttackGuard;
import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Cadastro com o padrão de assinatura do ataque não nasce — e o IP de origem entra na lista na
 * mesma requisição.
 *
 * Depois de 20/10 e 22/10 (contas "probe-x3mm", "probe-p914", sempre do mesmo provedor de
 * hospedagem), esperar a triagem automática é esperar o atacante criar o próximo lote. Aqui a
 * contenção é imediata: o nome/username derivado bate no padrão configurado
 * ({@code rede.security.suspicious.name-patterns}, o mesmo da triagem) → 403 genérico, sem
 * ensinar a regra, e {@code blocked_ips} ganha o endereço (cadastro e login daquele IP param).
 *
 * O bloqueio é reversível no painel (/admin/blocked-ips/{ip}/unblock), então um falso positivo
 * tem conserto em um clique.
 */
@Service
public class SuspiciousSignupGuard implements SignupAttackGuard {

	private static final Logger LOG = LoggerFactory.getLogger(SuspiciousSignupGuard.class);
	/** Quem aparece na trilha do bloqueio: não é uma pessoa, é a automação. */
	static final String ACTOR = "automod:cadastro";

	private final IpBlockService ipBlocks;
	private final boolean enabled;
	private final List<String> patterns;

	public SuspiciousSignupGuard(IpBlockService ipBlocks,
			@Value("${rede.security.signup-guard-enabled:true}") boolean enabled,
			@Value("${rede.security.suspicious.name-patterns:probe}") String patterns) {
		this.ipBlocks = ipBlocks;
		this.enabled = enabled;
		this.patterns = Arrays.stream(patterns.split(","))
			.map(String::trim)
			.filter(pattern -> !pattern.isEmpty())
			.map(pattern -> pattern.toLowerCase(Locale.ROOT))
			.toList();
	}

	@Override
	public void checkSignup(String name, String email, String ip) {
		if (!enabled || !StringUtils.hasText(name) || patterns.isEmpty()) {
			return;
		}
		String typed = name.trim().toLowerCase(Locale.ROOT);
		if (patterns.stream().noneMatch(typed::contains)) {
			return;
		}
		blockIp(ip);
		LOG.warn("Cadastro recusado por padrão de ataque (ip={}, email={})", ip, mask(email));
		// Mensagem genérica: o atacante não descobre qual palavra disparou o bloqueio.
		throw new ApiException(403, "SIGNUP_REFUSED",
			"Não foi possível criar a conta agora. Tente novamente mais tarde.");
	}

	private void blockIp(String ip) {
		if (!StringUtils.hasText(ip) || "desconhecido".equals(ip)) {
			return;
		}
		try {
			ipBlocks.block(ACTOR, ip, "Cadastro com padrão de ataque conhecido.");
		}
		catch (ApiException alreadyBlocked) {
			// 409 = já estava bloqueado; o efeito desejado continua valendo.
		}
		catch (RuntimeException failure) {
			LOG.warn("Não foi possível bloquear o IP {} do cadastro recusado: {}", ip, failure.getMessage());
		}
	}

	/** O e-mail não vai inteiro para o log: só o bastante para o time reconhecer a conta. */
	private String mask(String email) {
		if (!StringUtils.hasText(email) || !email.contains("@")) {
			return "?";
		}
		int at = email.indexOf('@');
		return email.charAt(0) + "***" + email.substring(at);
	}
}
