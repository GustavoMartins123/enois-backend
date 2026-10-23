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
		String remote = request.getRemoteAddr();
		// Só um proxy da nossa rede pode ditar o IP do cliente. Se a conexão vier direto da
		// internet, X-Forwarded-For/X-Real-IP são do atacante e não valem (E-12/E-27).
		if (!isTrustedProxy(remote)) {
			return isPlausible(remote) ? remote.trim() : "desconhecido";
		}
		String forwarded = request.getHeader("X-Forwarded-For");
		if (StringUtils.hasText(forwarded)) {
			// Behind the ALB the header is always there. If its last hop is not an IP, fall to the
			// connection address, never to X-Real-IP: the ALB passes that header through untouched,
			// so the client would pick its own key (bypassing rate limits and IP blocks).
			String candidate = lastHop(forwarded);
			if (isPlausible(candidate)) {
				return candidate;
			}
			return isPlausible(remote) ? remote.trim() : "desconhecido";
		}
		// X-Real-IP NUNCA é usado: o nginx do QA não o sobrescreve, então o cliente escolhia o
		// próprio IP e escapava de bloqueio/rate limit (N-08). O proxy deve mandar X-Forwarded-For
		// (com o IP real no FIM); sem ele, vale a conexão.
		return isPlausible(remote) ? remote.trim() : "desconhecido";
	}

	/** Loopback e faixas privadas: é onde vivem o nginx/ALB do cluster. */
	private boolean isTrustedProxy(String ip) {
		if (!StringUtils.hasText(ip)) {
			return false;
		}
		String value = ip.trim().toLowerCase(java.util.Locale.ROOT);
		if (value.startsWith("::ffff:")) {
			value = value.substring(7);
		}
		if (value.equals("127.0.0.1") || value.equals("::1") || value.equals("0:0:0:0:0:0:0:1")) {
			return true;
		}
		if (value.startsWith("10.") || value.startsWith("192.168.") || value.startsWith("169.254.")) {
			return true;
		}
		if (value.startsWith("172.")) {
			String[] parts = value.split("\\.");
			if (parts.length > 1) {
				try {
					int second = Integer.parseInt(parts[1]);
					return second >= 16 && second <= 31;
				}
				catch (NumberFormatException ignored) {
					return false;
				}
			}
		}
		return value.startsWith("fc") || value.startsWith("fd") || value.startsWith("fe80:");
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
