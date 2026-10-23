package com.socialcoisa.rede.apiv1.origins;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * CORS da API pública: uma origem só recebe `Access-Control-Allow-Origin` se estiver cadastrada
 * em {@code /developers/origins} (ou for uma origem do próprio app). Sem origem cadastrada, o
 * preflight responde 403 — é o "definir a origem para usar a API".
 *
 * Chamadas sem `Origin` (servidor-a-servidor, curl) seguem normalmente: CORS é regra de navegador.
 * A origem do app continua com o CORS estático de {@code CorsConfig} (nós só cuidamos das
 * origens de terceiros para não duplicar cabeçalho).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 4)
class PublicApiCorsFilter extends OncePerRequestFilter {

	private final DeveloperOriginService origins;
	private final Set<String> staticOrigins;

	PublicApiCorsFilter(DeveloperOriginService origins,
			@Value("${rede.cors.allowed-origins:https://enois.ia.br,http://localhost:5173}") String allowed) {
		this.origins = origins;
		this.staticOrigins = Arrays.stream(allowed.split(","))
			.map(String::trim)
			.filter(StringUtils::hasText)
			.map(PublicApiCorsFilter::matchForm)
			.collect(Collectors.toUnmodifiableSet());
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (!isV1(path(request))) {
			chain.doFilter(request, response);
			return;
		}
		String origin = request.getHeader("Origin");
		if (!StringUtils.hasText(origin)) {
			chain.doFilter(request, response);
			return;
		}
		String normalized = matchForm(origin);
		boolean appOrigin = staticOrigins.contains(normalized);
		boolean registered = origins.allowedOrigins().contains(normalized);
		if (!appOrigin && !registered) {
			response.setStatus(403);
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			response.setCharacterEncoding("UTF-8");
			response.getWriter()
				.write("{\"code\":\"ORIGIN_NOT_ALLOWED\",\"message\":\"Cadastre a origem do seu app em "
					+ "/developers/origins para usar a API publica.\"}");
			return;
		}
		if (appOrigin) {
			// O CORS estático do app (CorsConfig) responde por esta origem — evita cabeçalho duplicado.
			chain.doFilter(request, response);
			return;
		}
		response.setHeader("Access-Control-Allow-Origin", origin);
		response.setHeader("Vary", "Origin");
		if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
			response.setHeader("Access-Control-Allow-Methods", "GET,POST,PUT,PATCH,DELETE,OPTIONS");
			String requested = request.getHeader("Access-Control-Request-Headers");
			response.setHeader("Access-Control-Allow-Headers", StringUtils.hasText(requested) ? requested : "*");
			response.setHeader("Access-Control-Max-Age", "600");
			response.setStatus(204);
			return;
		}
		chain.doFilter(request, response);
	}

	private static boolean isV1(String path) {
		return path.equals("/v1") || path.startsWith("/v1/");
	}

	private static String path(HttpServletRequest request) {
		String path = request.getRequestURI();
		String context = request.getContextPath();
		if (context != null && !context.isEmpty() && path.startsWith(context)) {
			path = path.substring(context.length());
		}
		return path;
	}

	/** Forma de comparação: esquema/host minúsculos e sem barra final. */
	static String matchForm(String origin) {
		String clean = origin == null ? "" : origin.trim().toLowerCase(Locale.ROOT);
		while (clean.endsWith("/")) {
			clean = clean.substring(0, clean.length() - 1);
		}
		return clean;
	}
}
