package com.socialcoisa.rede.shared.security;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Descobre de qual IP veio o pedido — o dado em que os limites por origem se apoiam.
 *
 * O tráfego entra pelo proxy reverso (Traefik), então {@code getRemoteAddr()} é o IP do proxy,
 * não o de quem pediu. O proxy deve enviar {@code X-Forwarded-For} com o IP real do cliente.
 *
 * Proxies confiáveis: faixas privadas (Docker, loopback) + IPs extras configuráveis via
 * {@code TRUSTED_PROXY_IPS}. Somente conexões vindas de um proxy confiável leem o XFF;
 * conexões diretas da internet usam o {@code getRemoteAddr()} para que o cliente não
 * escolha o próprio IP via header forjado.
 *
 * O Traefik coloca o IP real como primeiro (ou único) item do XFF. O ALB da AWS colocava
 * no fim. O resolver lê o PRIMEIRO hop do XFF (o mais à esquerda), que é o IP do cliente
 * original. Itens subsequentes são proxies intermediários.
 */
@Component
public class ClientIpResolver {

	private static final int MAX_IP = 45;

	private final Set<String> extraTrustedIps;

	public ClientIpResolver(
			@Value("${rede.security.trusted-proxy-ips:}") String trustedProxyIps) {
		this.extraTrustedIps = java.util.Arrays.stream(trustedProxyIps.split(","))
			.map(String::trim)
			.filter(s -> !s.isEmpty())
			.map(s -> s.toLowerCase(Locale.ROOT))
			.collect(Collectors.toUnmodifiableSet());
	}

	public String resolve(HttpServletRequest request) {
		String remote = request.getRemoteAddr();
		if (!isTrustedProxy(remote)) {
			return isPlausible(remote) ? remote.trim() : "desconhecido";
		}
		String forwarded = request.getHeader("X-Forwarded-For");
		if (StringUtils.hasText(forwarded)) {
			String candidate = firstHop(forwarded);
			if (isPlausible(candidate)) {
				return candidate;
			}
			return isPlausible(remote) ? remote.trim() : "desconhecido";
		}
		return isPlausible(remote) ? remote.trim() : "desconhecido";
	}

	/** Loopback, faixas privadas e IPs extras configurados via env. */
	private boolean isTrustedProxy(String ip) {
		if (!StringUtils.hasText(ip)) {
			return false;
		}
		String value = ip.trim().toLowerCase(Locale.ROOT);
		if (value.startsWith("::ffff:")) {
			value = value.substring(7);
		}
		if (extraTrustedIps.contains(value)) {
			return true;
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
	 * Primeiro item do XFF — o IP do cliente original. O Traefik coloca o IP real do
	 * cliente como primeiro item; proxies intermediários são adicionados depois.
	 */
	private String firstHop(String header) {
		int firstComma = header.indexOf(',');
		return firstComma < 0 ? header.trim() : header.substring(0, firstComma).trim();
	}

	private boolean isPlausible(String value) {
		if (!StringUtils.hasText(value) || value.length() > MAX_IP || StringUtils.containsWhitespace(value)) {
			return false;
		}
		return value.matches("[0-9a-fA-F:.]{3,}");
	}
}
