package com.socialcoisa.rede.shared.web;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import com.socialcoisa.rede.shared.security.ClientIpResolver;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * One access log line per request plus the MDC every other log line inherits (requestId, clientIp,
 * and userId once {@link CurrentUserResolver} authenticates). Runs right after Spring's observation
 * filter (HIGHEST_PRECEDENCE + 1, which opens the trace) and before the rate limiters (@Order 1/2),
 * so 429s are logged and carry traceId.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class RequestObservabilityFilter extends OncePerRequestFilter {

	static final String REQUEST_ID_HEADER = "X-Request-Id";
	public static final String MDC_CLIENT_IP = "clientIp";
	static final String MDC_USER_ID = "userId";

	private static final int MAX_USER_AGENT = 200;

	private static final Logger LOGGER = LoggerFactory.getLogger(RequestObservabilityFilter.class);
	// Separate logger name so admin actions can be queried/retained on their own: {logger="audit"}.
	private static final Logger AUDIT = LoggerFactory.getLogger("audit");

	private final ClientIpResolver clientIpResolver;

	RequestObservabilityFilter(ClientIpResolver clientIpResolver) {
		this.clientIpResolver = clientIpResolver;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String requestId = requestId(request);
		String clientIp = clientIpResolver.resolve(request);
		long startedAt = System.nanoTime();
		MDC.put("requestId", requestId);
		MDC.put(MDC_CLIENT_IP, clientIp);
		response.setHeader(REQUEST_ID_HEADER, requestId);
		try {
			filterChain.doFilter(request, response);
		}
		finally {
			long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
			String method = request.getMethod();
			String route = route(request);
			int status = response.getStatus();
			String userId = MDC.get(MDC_USER_ID);
			LOGGER.atInfo()
				.addKeyValue("http.method", method)
				.addKeyValue("http.route", route)
				.addKeyValue("http.path", request.getRequestURI())
				.addKeyValue("http.status", status)
				.addKeyValue("durationMs", durationMs)
				.addKeyValue("userAgent", userAgent(request))
				.log("http_request requestId={} method={} path={} status={} durationMs={} clientIp={} userId={}",
					requestId, method, request.getRequestURI(), status, durationMs, clientIp, userId);
			if (isAdminMutation(method, request.getRequestURI())) {
				AUDIT.atInfo()
					.addKeyValue("audit.action", method + " " + route)
					.addKeyValue("audit.path", request.getRequestURI())
					.addKeyValue("audit.status", status)
					.log("admin_action actor={} action={} {} path={} status={}", userId, method, route,
						request.getRequestURI(), status);
			}
			MDC.remove("requestId");
			MDC.remove(MDC_CLIENT_IP);
			MDC.remove(MDC_USER_ID);
		}
	}

	/** Route template (/admin/reports/{reportId}): bounded cardinality for grouping, unlike the raw path. */
	private static String route(HttpServletRequest request) {
		Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
		return pattern != null ? pattern.toString() : "UNMATCHED";
	}

	// ponytail: path-based audit covers every /admin/** mutation in one place; per-action audit with
	// target/before/after needs explicit calls in the admin services.
	private static boolean isAdminMutation(String method, String uri) {
		return uri.contains("/admin/") && !"GET".equals(method) && !"HEAD".equals(method)
			&& !"OPTIONS".equals(method);
	}

	/** Client-controlled: truncated and stripped of control chars so it cannot forge log lines. */
	private static String userAgent(HttpServletRequest request) {
		String value = request.getHeader("User-Agent");
		if (value == null) {
			return null;
		}
		String clean = value.replaceAll("\\p{Cntrl}", "");
		return clean.length() > MAX_USER_AGENT ? clean.substring(0, MAX_USER_AGENT) : clean;
	}

	private String requestId(HttpServletRequest request) {
		String header = request.getHeader(REQUEST_ID_HEADER);
		// O valor vem do cliente e vai para header de resposta e para o log: aceita apenas um
		// identificador seguro para nao permitir injecao de cabecalho/log.
		if (header != null && header.matches("^[A-Za-z0-9._-]{8,64}$")) {
			return header;
		}
		return UUID.randomUUID().toString().replace("-", "");
	}
}
