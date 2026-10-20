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
 * <p>Chave: X-Real-IP, depois primeira entrada de X-Forward-For, depois remoteAddr. Quando não
 * há header nenhum (tráfego interno do cluster atrás do ingress), o balde fica compartilhado —
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
	private final int perMinute;
	private final int sharedMultiplier;

	public GlobalApiRateLimitFilter(Clock clock,
			@Value("${rede.rate.global-per-minute:300}") int perMinute,
			@Value("${rede.rate.global-shared-multiplier:50}") int sharedMultiplier) {
		this.clock = clock;
		this.perMinute = perMinute;
		this.sharedMultiplier = Math.max(1, sharedMultiplier);
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String uri = request.getRequestURI();
		return uri.contains("/actuator") || uri.contains("/health");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (perMinute <= 0) {
			chain.doFilter(request, response);
			return;
		}
		String realIp = request.getHeader("X-Real-IP");
		String forwarded = request.getHeader("X-Forwarded-For");
		boolean hasHeader = StringUtils.hasText(realIp) || StringUtils.hasText(forwarded);
		String key = ClientIp.extract(realIp, forwarded);
		if (!hasHeader) {
			String remote = request.getRemoteAddr();
			if (StringUtils.hasText(remote)) {
				key = remote;
			}
		}
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
