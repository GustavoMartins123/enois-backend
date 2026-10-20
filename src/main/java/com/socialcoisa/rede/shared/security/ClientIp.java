package com.socialcoisa.rede.shared.security;

import org.springframework.util.StringUtils;

/**
 * Extrai o IP do cliente a partir dos headers de proxy, sem o request em maos. Preferencia:
 * X-Real-IP (o ingress escreve a partir da conexao real) e, na falta dele, a PRIMEIRA entrada
 * de X-Forwarded-For — mesma escolha do AuthThrottle, para os dois mecanismos verem o mesmo
 * valor. Para decisoes em que o cliente pode forjar header, use {@link ClientIpResolver}
 * (ultimo hop, o que o balanceador acrescentou).
 */
public final class ClientIp {

	private ClientIp() {
	}

	public static String extract(String realIp, String forwardedFor) {
		if (StringUtils.hasText(realIp)) {
			return realIp.trim();
		}
		if (StringUtils.hasText(forwardedFor)) {
			String first = forwardedFor.split(",")[0].trim();
			if (StringUtils.hasText(first)) {
				return first;
			}
		}
		return "desconhecido";
	}
}
