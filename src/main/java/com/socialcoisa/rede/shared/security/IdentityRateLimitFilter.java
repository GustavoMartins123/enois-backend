package com.socialcoisa.rede.shared.security;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Aplica o limite por IP nas rotas de identidade — cadastro, entrada, senha, e-mail e segundo fator.
 *
 * As regras de caminho centralizam a protecao, sem uma cota compartilhada por todos os clientes.
 * Novas operacoes de identidade devem entrar neste mapa; OAuth cobre tambem GET e DELETE.
 *
 * A resposta é 429 com o mesmo formato de erro do resto da API, sem dizer quantas tentativas faltam
 * nem qual é o teto — informar o teto ajuda quem está atacando a calibrar o ataque.
 */
@Component
@Order(2)
class IdentityRateLimitFilter extends OncePerRequestFilter {

	/** Regras POST por caminho; as operacoes OAuth de caminho variavel sao resolvidas abaixo. */
	private static final Map<String, String> RULES = Map.ofEntries(
		Map.entry("/auth/signup", IpRateLimiter.SIGNUP),
		Map.entry("/auth/login", IpRateLimiter.LOGIN),
		Map.entry("/auth/password/forgot", IpRateLimiter.PASSWORD),
		Map.entry("/auth/password/reset", IpRateLimiter.PASSWORD),
		Map.entry("/auth/password/change", IpRateLimiter.PASSWORD),
		Map.entry("/auth/email/resend", IpRateLimiter.EMAIL),
		Map.entry("/auth/email/verify", IpRateLimiter.EMAIL),
		Map.entry("/auth/email/change", IpRateLimiter.EMAIL),
		Map.entry("/auth/2fa/resend", IpRateLimiter.EMAIL),
		Map.entry("/auth/2fa/email/request", IpRateLimiter.EMAIL),
		Map.entry("/auth/2fa/verify", IpRateLimiter.TWO_FACTOR),
		Map.entry("/auth/2fa/email/confirm", IpRateLimiter.TWO_FACTOR),
		Map.entry("/auth/2fa/email/disable", IpRateLimiter.TWO_FACTOR),
		Map.entry("/auth/2fa/totp/setup", IpRateLimiter.TWO_FACTOR),
		Map.entry("/auth/2fa/totp/enable", IpRateLimiter.TWO_FACTOR),
		Map.entry("/auth/2fa/totp/disable", IpRateLimiter.TWO_FACTOR),
		Map.entry("/auth/refresh", IpRateLimiter.LOGIN),
		Map.entry("/auth/account/delete", IpRateLimiter.PASSWORD));

	private final IpRateLimiter limiter;
	private final ClientIpResolver ipResolver;

	IdentityRateLimitFilter(IpRateLimiter limiter, ClientIpResolver ipResolver) {
		this.limiter = limiter;
		this.ipResolver = ipResolver;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String action = actionFor(request);
		if (action != null && !limiter.tryAcquire(action, ipResolver.resolve(request))) {
			writeTooManyRequests(response);
			return;
		}
		filterChain.doFilter(request, response);
	}

	/** A rota da regra, já sem o prefixo de contexto (`/api` em produção, vazio nos testes). */
	private String actionFor(HttpServletRequest request) {
		String path = request.getRequestURI();
		String context = request.getContextPath();
		if (context != null && !context.isEmpty() && path.startsWith(context)) {
			path = path.substring(context.length());
		}
		String normalized = path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
		if ("GET".equalsIgnoreCase(request.getMethod())
				&& normalized.matches("/auth/oauth/[^/]+/(?:start|callback)")) return IpRateLimiter.LOGIN;
		if ("DELETE".equalsIgnoreCase(request.getMethod())
				&& normalized.matches("/auth/oauth/[^/]+/link")) return IpRateLimiter.LOGIN;
		if (!"POST".equalsIgnoreCase(request.getMethod())) return null;
		if (normalized.matches("/auth/oauth/[^/]+/(?:token|callback|link)")) return IpRateLimiter.LOGIN;
		return RULES.get(normalized);
	}

	private void writeTooManyRequests(HttpServletResponse response) throws IOException {
		response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		Map<String, String> body = new LinkedHashMap<>();
		body.put("code", "RATE_LIMITED");
		body.put("message", "Muitas tentativas em sequência. Espere alguns minutos e tente de novo.");
		body.put("timestamp", Instant.now().toString());
		response.getWriter().write(toJson(body));
	}

	/** JSON montado à mão: o filtro roda antes do MVC, então não há ObjectMapper injetado aqui. */
	private String toJson(Map<String, String> body) {
		StringBuilder json = new StringBuilder("{");
		List<Map.Entry<String, String>> entries = List.copyOf(body.entrySet());
		for (int i = 0; i < entries.size(); i++) {
			Map.Entry<String, String> entry = entries.get(i);
			if (i > 0) {
				json.append(',');
			}
			json.append('"').append(entry.getKey()).append("\":\"").append(escape(entry.getValue())).append('"');
		}
		return json.append('}').toString();
	}

	private String escape(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
