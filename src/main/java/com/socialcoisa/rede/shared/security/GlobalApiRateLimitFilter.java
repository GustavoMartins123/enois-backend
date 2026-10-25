package com.socialcoisa.rede.shared.security;

import java.io.IOException;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Rate limit GLOBAL por IP para toda a API — a última linha de defesa contra varredura, flood e
 * scripts que martelam endpoints variados (os limites por usuário e por ação ficam nos guards
 * específicos; este é o teto de superfície inteira).
 *
 * <p>Key: {@link ClientIpResolver} (last X-Forwarded-For hop, the one the ALB appends). Client-sent
 * X-Real-IP or a forged first XFF entry must not pick the bucket, or rotating it bypasses the limit.
 * Quando não há header nenhum (tráfego interno do cluster atrás do ingress), o balde fica compartilhado —
 * e ganha um limite multiplicado para não estrangular humanos se o ingress não mandar headers.
 * Endpoints de sonda (actuator) passam fora: derrubar o health check derruba o pod.
 */
@Component
@Order(1)
public class GlobalApiRateLimitFilter extends OncePerRequestFilter {

	private static final Logger LOG = LoggerFactory.getLogger(GlobalApiRateLimitFilter.class);

	private static final int MAX_KEYS = 100_000;
	private static final long WINDOW_MS = 60_000L;

	private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();
	private final Clock clock;
	private final ClientIpResolver clientIpResolver;
	private final int perMinute;
	private final int sharedMultiplier;

	public GlobalApiRateLimitFilter(Clock clock, ClientIpResolver clientIpResolver,
			@Value("${rede.rate.global-per-minute:0}") int perMinute,
			@Value("${rede.rate.global-shared-multiplier:50}") int sharedMultiplier) {
		this.clock = clock;
		this.clientIpResolver = clientIpResolver;
		this.perMinute = perMinute;
		this.sharedMultiplier = Math.max(1, sharedMultiplier);
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String uri = request.getRequestURI();
		if (uri.contains("/actuator") || uri.contains("/health")) {
			return true;
		}
		// Leitura nao entra no teto. Uma tela do app dispara varias chamadas ao abrir e o feed se
		// atualiza sozinho: punir GET transformava navegacao normal em 429. O que precisa de teto
		// e a ESCRITA (POST/PUT/PATCH/DELETE), que e onde ha custo e abuso possivel.
		String metodo = request.getMethod();
		return "GET".equalsIgnoreCase(metodo) || "HEAD".equalsIgnoreCase(metodo)
			|| "OPTIONS".equalsIgnoreCase(metodo);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (perMinute <= 0) {
			chain.doFilter(request, response);
			return;
		}
		boolean hasHeader = StringUtils.hasText(request.getHeader("X-Forwarded-For"))
			|| StringUtils.hasText(request.getHeader("X-Real-IP"));
		String key = clientIpResolver.resolve(request);
		int limit = hasHeader ? perMinute : perMinute * sharedMultiplier;

		long now = clock.millis();
		if (hits.size() > MAX_KEYS) {
			sweep(now);
		}
		Deque<Long> timestamps = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
		boolean allowed;
		synchronized (timestamps) {
			while (!timestamps.isEmpty() && now - timestamps.peekFirst() >= WINDOW_MS) {
				timestamps.pollFirst();
			}
			if (timestamps.size() >= limit) {
				allowed = false;
			}
			else {
				timestamps.addLast(now);
				allowed = true;
			}
		}
		if (!allowed) {
			LOG.warn("Rate limit global atingido (ip: {})", key);
			response.setStatus(429);
			response.setContentType("application/json");
			response.getWriter().write("{\"error\":\"RATE_LIMITED\",\"message\":\"Muitas requisicoes. Tente novamente em instantes.\"}");
			return;
		}
		chain.doFilter(request, response);
	}

	private void sweep(long now) {
		hits.entrySet().removeIf(entry -> {
			Deque<Long> timestamps = entry.getValue();
			synchronized (timestamps) {
				while (!timestamps.isEmpty() && now - timestamps.peekFirst() >= WINDOW_MS) {
					timestamps.pollFirst();
				}
				return timestamps.isEmpty();
			}
		});
	}
}
