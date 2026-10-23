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
 * Toda requisição precisa de uma origem cadastrada para ser atendida.
 *
 * Navegador manda {@code Origin} em requisições cross-origin (e em POST/PUT/DELETE same-origin);
 * chamadas de servidor (SSR, curl, integrações) sempre podem mandar {@code X-Client-Origin}. As
 * duas formas passam pela mesma lista: origens do próprio app (CORS estático) + origens que o
 * desenvolvedor cadastrou em {@code /developers/origins}.
 *
 * Regras:
 * <ul>
 * <li>{@code Origin} presente: é a autoridade. Desconhecida → 403 ORIGIN_NOT_ALLOWED; nunca cai
 * para o {@code X-Client-Origin} (senão um site malicioso poderia falsificar a origem).</li>
 * <li>Sem {@code Origin} e com {@code X-Client-Origin}: mesma validação.</li>
 * <li>Sem nenhum dos dois: 403 ORIGIN_REQUIRED, exceto nos fluxos públicos/infra listados em
 * {@link #bypass} e {@link #allowsNoOrigin}.</li>
 * </ul>
 *
 * CORS não é autenticação: um cliente de servidor pode mentir o {@code X-Client-Origin}, mas ele
 * também não é protegido por CORS. O gate protege o navegador (CSRF/leitura cross-origin) e
 * documenta quem consome a API.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 4)
class RequestOriginFilter extends OncePerRequestFilter {

	static final String CLIENT_ORIGIN_HEADER = "X-Client-Origin";

	private final DeveloperOriginService origins;
	private final Set<String> staticOrigins;
	private final boolean enforce;

	RequestOriginFilter(DeveloperOriginService origins,
			@Value("${rede.cors.allowed-origins:https://enois.ia.br,http://localhost:5173}") String allowed,
			@Value("${rede.origins.enforce:true}") boolean enforce) {
		this.origins = origins;
		this.staticOrigins = Arrays.stream(allowed.split(","))
			.map(String::trim)
			.filter(StringUtils::hasText)
			.map(RequestOriginFilter::matchForm)
			.collect(Collectors.toUnmodifiableSet());
		this.enforce = enforce;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (!enforce) {
			chain.doFilter(request, response);
			return;
		}
		String path = path(request);
		String method = request.getMethod();

		// Fluxos controlados por terceiros ou infraestrutura que não têm origem do app.
		if (bypass(method, path)) {
			chain.doFilter(request, response);
			return;
		}

		String origin = request.getHeader("Origin");
		if (StringUtils.hasText(origin)) {
			String normalized = matchForm(origin);
			if (origins.allowedOrigins().contains(normalized)) {
				// Origem de terceiro cadastrada: o CORS estático não responde por ela.
				response.setHeader("Access-Control-Allow-Origin", origin);
				response.setHeader("Vary", "Origin");
				if (preflight(request, response)) {
					return;
				}
				chain.doFilter(request, response);
				return;
			}
			if (staticOrigins.contains(normalized)) {
				// Origem do app: o CORS estático (CorsConfig) responde, inclusive no preflight.
				chain.doFilter(request, response);
				return;
			}
			reject(response, "ORIGIN_NOT_ALLOWED",
				"Cadastre a origem do seu app em /developers/origins para usar a API.");
			return;
		}

		String clientOrigin = request.getHeader(CLIENT_ORIGIN_HEADER);
		if (StringUtils.hasText(clientOrigin)) {
			String normalized = matchForm(clientOrigin);
			if (origins.allowedOrigins().contains(normalized) || staticOrigins.contains(normalized)) {
				chain.doFilter(request, response);
				return;
			}
			reject(response, "ORIGIN_NOT_ALLOWED",
				"Cadastre a origem do seu app em /developers/origins para usar a API.");
			return;
		}

		if (allowsNoOrigin(method, path)) {
			chain.doFilter(request, response);
			return;
		}
		reject(response, "ORIGIN_REQUIRED",
			"Toda requisicao precisa de origem cadastrada. O navegador envia Origin; chamadas de "
				+ "servidor devem enviar X-Client-Origin com uma origem cadastrada.");
	}

	/** Responde o preflight de uma origem de terceiro cadastrada. */
	private static boolean preflight(HttpServletRequest request, HttpServletResponse response)
			throws IOException {
		if (!"OPTIONS".equalsIgnoreCase(request.getMethod())) {
			return false;
		}
		response.setHeader("Access-Control-Allow-Methods", "GET,POST,PUT,PATCH,DELETE,OPTIONS");
		String requested = request.getHeader("Access-Control-Request-Headers");
		response.setHeader("Access-Control-Allow-Headers", StringUtils.hasText(requested) ? requested : "*");
		response.setHeader("Access-Control-Max-Age", "600");
		response.setStatus(204);
		return true;
	}

	/**
	 * Rotas que pertencem a outro sistema (GitHub, provedores OAuth, gov.br) ou à infraestrutura.
	 * Elas autenticam por conta própria (HMAC, state/nonce) e não têm a origem do app.
	 */
	private static boolean bypass(String method, String path) {
		if (path.equals("/health") || path.equals("/version")) {
			return true;
		}
		if (path.startsWith("/actuator")) {
			return true;
		}
		if (path.startsWith("/webhooks/")) {
			return true;
		}
		if (path.startsWith("/identity/govbr/")) {
			return true;
		}
		// GET de início e callbacks OAuth: navegação/redirect do provedor, sem Origin do app.
		return path.startsWith("/auth/oauth/")
				&& ("GET".equalsIgnoreCase(method) || path.endsWith("/callback"));
	}

	/**
	 * Conteúdo público que pode ser aberto direto no navegador, carregado como imagem ou clicado
	 * de um e-mail. Se vier uma origem, ela ainda é validada antes (ver {@link #doFilterInternal}).
	 */
	private static boolean allowsNoOrigin(String method, String path) {
		if (path.startsWith("/pages/")) {
			return true;
		}
		if (path.startsWith("/media/") && "GET".equalsIgnoreCase(method)) {
			return true;
		}
		return path.equals("/blogs/descadastrar");
	}

	private static void reject(HttpServletResponse response, String code, String message) throws IOException {
		response.setStatus(403);
		// Um CDN/proxy não pode guardar o bloqueio e devolvê-lo para uma origem válida depois.
		response.setHeader("Cache-Control", "no-store");
		response.setHeader("Vary", "Origin");
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
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
