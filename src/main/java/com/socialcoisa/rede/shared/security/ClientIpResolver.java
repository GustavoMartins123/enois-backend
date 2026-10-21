package com.socialcoisa.rede.shared.security;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Descobre de qual IP veio o pedido — o dado em que os limites por origem se apoiam.
 *
 * O tráfego entra pelo ALB, então `getRemoteAddr()` é o IP do nó do cluster, não o de quem pediu:
 * qualquer controle "por IP" que lesse dali estaria limitando o mundo inteiro como se fosse um só.
 *
 * Sobre confiar no `X-Forwarded-For`: o cabeçalho pode vir forjado pelo cliente, mas o ALB
 * **acrescenta** o IP de quem se conectou ao FIM da lista. Logo o ÚLTIMO item é o único que nós
 * (e não o cliente) escrevemos — é ele que vale. Os itens anteriores são histórico que o cliente
 * pode ter inventado, e por isso não entram na decisão.
 *
 * Limites de sanidade: valor curto, sem espaço, com forma de IP; qualquer coisa fora disso cai no
 * `getRemoteAddr()` (nunca lança, nunca deixa o cabeçalho virar chave de mapa gigante).
 */
@Component
public class ClientIpResolver {

	private static final int MAX_IP = 45;

	public String resolve(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");
		if (StringUtils.hasText(forwarded)) {
			// Behind the ALB the header is always there. If its last hop is not an IP, fall to the
			// connection address, never to X-Real-IP: the ALB passes that header through untouched,
			// so the client would pick its own key (bypassing rate limits and IP blocks).
			String candidate = lastHop(forwarded);
			if (isPlausible(candidate)) {
				return candidate;
			}
			String remote = request.getRemoteAddr();
			return isPlausible(remote) ? remote.trim() : "desconhecido";
		}
		String real = request.getHeader("X-Real-IP");
		if (isPlausible(real)) {
			return real.trim();
		}
		String remote = request.getRemoteAddr();
		return isPlausible(remote) ? remote.trim() : "desconhecido";
	}

	/**
	 * O último item da lista — o que o nosso balanceador acrescentou. No length cap on the whole
	 * header: the client can pad the forged prefix, and the ALB still appends the real IP at the end.
	 * The hop itself is bounded by {@link #isPlausible}.
	 */
	private String lastHop(String header) {
		int lastComma = header.lastIndexOf(',');
		return lastComma < 0 ? header.trim() : header.substring(lastComma + 1).trim();
	}

	/**
	 * Forma de IP (v4 ou v6). Não valida faixa nem faz DNS: aqui é só para não aceitar texto solto
	 * como chave — a decisão de bloquear é do limitador, não desta classe.
	 */
	private boolean isPlausible(String value) {
		if (!StringUtils.hasText(value) || value.length() > MAX_IP || StringUtils.containsWhitespace(value)) {
			return false;
		}
		return value.matches("[0-9a-fA-F:.]{3,}");
	}
}
