package com.socialcoisa.rede.shared.security;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Limite de ações por usuário autenticado — complementa o {@link IpRateLimiter} (que atua por IP).
 *
 * O IpRateLimiter protege autenticação (signup, login). Este protege ações dentro do app: publicar
 * posts, comentar, candidatar-se a vagas e enviar mensagens. A mecânica é a mesma (janela
 * deslizante em memória, sem Redis), e a mesma consequência se aplica: o limite vale por pod.
 */
@Component
public class UserRateLimiter {

	public static final String CREATE_POST = "create_post";
	public static final String CREATE_COMMENT = "create_comment";
	public static final String APPLY_JOB = "apply_job";
	public static final String SEND_MESSAGE = "send_message";

	private static final int MAX_KEYS = 50_000;

	private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();
	private final Clock clock;

	private final int postLimit;
	private final long postWindowMs;
	private final int commentLimit;
	private final long commentWindowMs;
	private final int jobApplyLimit;
	private final long jobApplyWindowMs;
	private final int messageLimit;
	private final long messageWindowMs;

	public UserRateLimiter(Clock clock,
			@Value("${rede.rate.post-limit:10}") int postLimit,
			@Value("${rede.rate.post-window-minutes:60}") long postWindowMinutes,
			@Value("${rede.rate.comment-limit:30}") int commentLimit,
			@Value("${rede.rate.comment-window-minutes:60}") long commentWindowMinutes,
			@Value("${rede.rate.job-apply-limit:100}") int jobApplyLimit,
			@Value("${rede.rate.job-apply-window-minutes:30}") long jobApplyWindowMinutes,
			@Value("${rede.rate.message-limit:60}") int messageLimit,
			@Value("${rede.rate.message-window-minutes:1}") long messageWindowMinutes) {
		this.clock = clock;
		this.postLimit = postLimit;
		this.postWindowMs = postWindowMinutes * 60_000L;
		this.commentLimit = commentLimit;
		this.commentWindowMs = commentWindowMinutes * 60_000L;
		this.jobApplyLimit = jobApplyLimit;
		this.jobApplyWindowMs = jobApplyWindowMinutes * 60_000L;
		this.messageLimit = messageLimit;
		this.messageWindowMs = messageWindowMinutes * 60_000L;
	}

	/**
	 * Verifica se o usuário pode executar a ação. Lança 429 se o limite foi atingido.
	 */
	public void require(String userId, String action) {
		if (!tryAcquire(userId, action)) {
			throw new ApiException(429, "RATE_LIMIT_EXCEEDED",
				"Voce atingiu o limite desta acao. Tente novamente em alguns minutos.");
		}
	}

	boolean tryAcquire(String userId, String action) {
		long now = clock.millis();
		long window = windowFor(action);
		int limit = limitFor(action);
		if (limit <= 0 || window <= 0) {
			return true;
		}
		if (hits.size() > MAX_KEYS) {
			sweep(now);
		}

		Deque<Long> timestamps = hits.computeIfAbsent(action + "|" + userId, key -> new ArrayDeque<>());
		synchronized (timestamps) {
			while (!timestamps.isEmpty() && now - timestamps.peekFirst() >= window) {
				timestamps.pollFirst();
			}
			if (timestamps.size() >= limit) {
				return false;
			}
			timestamps.addLast(now);
			return true;
		}
	}

	private long windowFor(String action) {
		return switch (action) {
			case CREATE_POST -> postWindowMs;
			case CREATE_COMMENT -> commentWindowMs;
			case APPLY_JOB -> jobApplyWindowMs;
			case SEND_MESSAGE -> messageWindowMs;
			default -> 0L;
		};
	}

	private int limitFor(String action) {
		return switch (action) {
			case CREATE_POST -> postLimit;
			case CREATE_COMMENT -> commentLimit;
			case APPLY_JOB -> jobApplyLimit;
			case SEND_MESSAGE -> messageLimit;
			default -> 0;
		};
	}

	private void sweep(long now) {
		hits.entrySet().removeIf(entry -> {
			Deque<Long> timestamps = entry.getValue();
			synchronized (timestamps) {
				String action = entry.getKey().substring(0, entry.getKey().indexOf('|'));
				long window = windowFor(action);
				while (!timestamps.isEmpty() && now - timestamps.peekFirst() >= window) {
					timestamps.pollFirst();
				}
				return timestamps.isEmpty();
			}
		});
	}
}
