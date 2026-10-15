package com.socialcoisa.rede.shared.web;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
class RequestObservabilityFilter extends OncePerRequestFilter {

	static final String REQUEST_ID_HEADER = "X-Request-Id";

	private static final Logger LOGGER = LoggerFactory.getLogger(RequestObservabilityFilter.class);

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String requestId = requestId(request);
		long startedAt = System.nanoTime();
		MDC.put("requestId", requestId);
		response.setHeader(REQUEST_ID_HEADER, requestId);
		try {
			filterChain.doFilter(request, response);
		}
		finally {
			long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
			LOGGER.info("http_request requestId={} method={} path={} status={} durationMs={}", requestId,
				request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs);
			MDC.remove("requestId");
		}
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
