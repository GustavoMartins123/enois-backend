package com.socialcoisa.rede.shared.web;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.socialcoisa.rede.shared.security.PanicModeService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Curto-circuito do modo pânico. Roda logo depois do access log (para o 429 aparecer no
 * CloudWatch) e antes dos rate limiters: com o pânico ligado, nenhuma escrita chega ao controller.
 *
 * Isentos: o próprio endpoint do pânico (precisa desligar) e os fluxos de entrada/recovery
 * (login, refresh, confirmação e 2FA) — bloquear o login impediria o admin de desligar.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 3)
public class PanicModeFilter extends OncePerRequestFilter {

	private static final Set<String> EXEMPT = Set.of(
		"/admin/panic",
		"/auth/login",
		"/auth/refresh",
		"/auth/2fa/verify",
		"/auth/2fa/resend",
		"/auth/2fa/email/request",
		"/auth/2fa/email/confirm");

	private final PanicModeService panic;

	PanicModeFilter(PanicModeService panic) {
		this.panic = panic;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (!panic.active() || !isWrite(request.getMethod()) || EXEMPT.contains(path(request))) {
			chain.doFilter(request, response);
			return;
		}
		response.setStatus(429);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		response.getWriter()
			.write("{\"code\":\"PANIC_MODE\",\"message\":\"Escritas temporariamente bloqueadas pelo modo panico. "
				+ "Tente novamente em instantes.\",\"timestamp\":\"" + Instant.now() + "\"}");
	}

	private static boolean isWrite(String method) {
		return "POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)
			|| "PATCH".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method);
	}

	private static String path(HttpServletRequest request) {
		String path = request.getRequestURI();
		String context = request.getContextPath();
		if (context != null && !context.isEmpty() && path.startsWith(context)) {
			path = path.substring(context.length());
		}
		return path;
	}
}
