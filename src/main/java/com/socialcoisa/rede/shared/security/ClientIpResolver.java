package com.socialcoisa.rede.shared.security;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Descobre de qual IP veio o pedido — o dado em que os limites por origem se apoiam.
 *
 * O tráfego entra por um proxy reverso (ALB na AWS antiga, Traefik em `docker-prod3` hoje), então
 * `getRemoteAddr()` é o IP do nó do proxy, não o de quem pediu: qualquer controle "por IP" que
 * lesse dali estaria limitando o mundo inteiro como se fosse um só. Foi exatamente isso que
 * aconteceu na migração pro Traefik — o IP do proxy (`31.220.102.169`, `docker-prod3`) não é mais
 * loopback/RFC1918 como o ALB antigo, então {@link #isTrustedProxy} rejeitava a conexão e todo
 * request caía no fallback de `getRemoteAddr()`, voltando sempre o IP do próprio proxy em vez do
 * cliente real — corrigido tornando a lista de proxies confiáveis configurável
 * (`security.trusted-proxy-ips`), em vez de só as faixas privadas fixas.
 *
 * A ordem das fontes: (1) `CF-Connecting-IP`, quando existe — é o IP que o Cloudflare viu, e
 * ele o reescreve a cada requisição (o cliente não forja); (2) a cadeia do `X-Forwarded-For`,
 * lida da direita para a esquerda pulando os proxies confiáveis; (3) o `getRemoteAddr()`.
 *
 * Sobre confiar no `X-Forwarded-For`: o cabeçalho pode vir forjado pelo cliente, e o item forjado
 * fica no MEIO da lista (o proxy acrescenta o IP real no fim) ou no COMEÇO (quando o proxy
 * substitui o cabeçalho e o cliente mandou um). Por isso a cadeia é lida da direita para a
 * esquerda, pulando os proxies confiáveis: o primeiro salto que não é proxy é o IP da pessoa —
 * com qualquer convenção do proxy, e sem o cliente poder escolher o próprio.
 */
@Component
public class ClientIpResolver {

	private static final int MAX_IP = 45;

	private final Set<String> trustedProxyIps;

	/** Default sem proxy extra confiável — usado pelos testes, mantém o comportamento anterior. */
	public ClientIpResolver() {
		this("");
	}

	@Autowired
	public ClientIpResolver(@Value("${security.trusted-proxy-ips:}") String trustedProxyIps) {
		this.trustedProxyIps = Arrays.stream(trustedProxyIps.split(","))
			.map(String::trim)
			.map(ip -> ip.toLowerCase(java.util.Locale.ROOT))
			.filter(StringUtils::hasText)
			.collect(Collectors.toUnmodifiableSet());
	}

	public String resolve(HttpServletRequest request) {
		String remote = request.getRemoteAddr();
		if (!isTrustedProxy(remote)) {
			return isPlausible(remote) ? remote.trim() : "desconhecido";
		}
		// O Cloudflare (que fica na frente do Traefik em produção) escreve o IP de quem pediu
		// neste cabeçalho e o SOBRESCREVE em toda requisição que passa por ele — o cliente não
		// consegue forjá-lo. É a única fonte do IP real quando o proxy da frente reescreve o
		// X-Forwarded-For com o endereço da borda dele (os logs mostraram faixas 172.6x, do
		// próprio Cloudflare, para todo mundo). Só vale vindo de um proxy confiável.
		String borda = request.getHeader("CF-Connecting-IP");
		if (isPlausible(borda)) {
			return borda.trim();
		}
		String forwarded = request.getHeader("X-Forwarded-For");
		if (StringUtils.hasText(forwarded)) {
			String candidate = clientHop(forwarded);
			if (isPlausible(candidate)) {
				return candidate;
			}
			return isPlausible(remote) ? remote.trim() : "desconhecido";
		}
		return isPlausible(remote) ? remote.trim() : "desconhecido";
	}

	/** Loopback, faixas privadas e o(s) IP(s) do proxy reverso atual: onde vivem nginx/ALB/Traefik. */
	private boolean isTrustedProxy(String ip) {
		if (!StringUtils.hasText(ip)) {
			return false;
		}
		String value = ip.trim().toLowerCase(Locale.ROOT);
		if (value.startsWith("::ffff:")) {
			value = value.substring(7);
		}
		if (trustedProxyIps.contains(value)) {
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
	 * O IP do cliente dentro da cadeia do `X-Forwarded-For`.
	 *
	 * <p>A cadeia é lida <strong>da direita para a esquerda</strong>, pulando os saltos que são
	 * proxies confiáveis. O único item da cadeia que o <strong>cliente</strong> controla é o mais
	 * à esquerda — e é exatamente esse que não pode valer: se ele valer, cada pessoa escolhe o
	 * próprio IP e escapa de todo limite por IP (e o bloqueio da moderação vira decorativo).
	 *
	 * <p>Assim a leitura serve a qualquer convenção do proxy:
	 * <ul>
	 * <li>proxy que <strong>substitui</strong> o cabeçalho (Traefik sem confiar em XFF de
	 * qualquer origem): a cadeia tem um item só, o IP real → é o primeiro não-confiável a partir
	 * da direita;</li>
	 * <li>proxy que <strong>acrescenta</strong> o IP de quem se conectou (ALB): a cadeia é
	 * "forjado, real" → da direita, "real" não é proxy confiável, é ele; o "forjado" (do cliente)
	 * nunca é alcançado;</li>
	 * <li>cadeia com proxies no meio: os confiáveis são pulados e para no primeiro que não é.</li>
	 * </ul>
	 */
	private String clientHop(String header) {
		String[] saltos = header.split(",");
		for (int i = saltos.length - 1; i >= 0; i--) {
			String salto = saltos[i].trim();
			if (!isPlausible(salto)) {
				continue;
			}
			if (isTrustedProxy(salto)) {
				continue;
			}
			return salto;
		}
		return "";
	}

	private boolean isPlausible(String value) {
		if (!StringUtils.hasText(value) || value.length() > MAX_IP || StringUtils.containsWhitespace(value)) {
			return false;
		}
		return value.matches("[0-9a-fA-F:.]{3,}");
	}
}
