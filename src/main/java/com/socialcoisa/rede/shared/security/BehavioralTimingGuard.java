package com.socialcoisa.rede.shared.security;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.socialcoisa.rede.moderation.domain.ReportReason;
import com.socialcoisa.rede.moderation.domain.TargetType;

/**
 * Detecta comportamento automatizado analisando o desvio padrao dos intervalos entre acoes
 * do mesmo usuario. Humanos tem cadencia irregular; bots tem intervalos quase identicos.
 *
 * <p>Guarda as ultimas {@value #WINDOW_SIZE} acoes por usuario (em memoria, Deque). Quando
 * o desvio padrao dos intervalos fica abaixo de {@value #STD_DEV_THRESHOLD_MS}ms, publica
 * um {@link AutoReportEvent} para a moderacao — sem bloquear a acao.
 */
@Component
public class BehavioralTimingGuard {

	private static final Logger LOG = LoggerFactory.getLogger(BehavioralTimingGuard.class);

	static final int WINDOW_SIZE = 10;
	static final double STD_DEV_THRESHOLD_MS = 500.0;
	private static final int MAX_KEYS = 50_000;
	/** Cooldown entre reports automaticos do mesmo usuario (1 hora). */
	private static final long REPORT_COOLDOWN_MS = 3_600_000L;

	private final Map<String, Deque<Long>> actions = new ConcurrentHashMap<>();
	private final Map<String, Long> lastReported = new ConcurrentHashMap<>();
	private final Clock clock;
	private final ApplicationEventPublisher events;

	public BehavioralTimingGuard(Clock clock, ApplicationEventPublisher events) {
		this.clock = clock;
		this.events = events;
	}

	/**
	 * Registra uma acao do usuario e analisa o padrao de tempo. Nunca bloqueia — apenas
	 * reporta se o padrao for suspeito.
	 */
	public void record(String userId) {
		long now = clock.millis();
		if (actions.size() > MAX_KEYS) {
			actions.clear();
		}

		Deque<Long> timestamps = actions.computeIfAbsent(userId, k -> new ArrayDeque<>());
		synchronized (timestamps) {
			timestamps.addLast(now);
			while (timestamps.size() > WINDOW_SIZE) {
				timestamps.pollFirst();
			}
			if (timestamps.size() < WINDOW_SIZE) {
				return;
			}
			double stdDev = stdDevOfIntervals(timestamps);
			if (stdDev < STD_DEV_THRESHOLD_MS) {
				reportIfCooldownExpired(userId, stdDev, now);
			}
		}
	}

	/**
	 * A cadencia desta conta esta com cara de automacao agora?
	 *
	 * <p>É o que o motor de risco pergunta antes de gastar uma consulta: a deteccao e feita em
	 * memoria (a janela de acoes), e nao publica nada — quem publica a denuncia automatica continua
	 * sendo este guarda.
	 */
	public boolean looksAutomated(String userId) {
		Deque<Long> timestamps = actions.get(userId);
		if (timestamps == null) {
			return false;
		}
		synchronized (timestamps) {
			if (timestamps.size() < WINDOW_SIZE) {
				return false;
			}
			return stdDevOfIntervals(timestamps) < STD_DEV_THRESHOLD_MS;
		}
	}

	static double stdDevOfIntervals(Deque<Long> timestamps) {
		long[] ts = timestamps.stream().mapToLong(Long::longValue).toArray();
		double[] intervals = new double[ts.length - 1];
		double sum = 0;
		for (int i = 1; i < ts.length; i++) {
			intervals[i - 1] = ts[i] - ts[i - 1];
			sum += intervals[i - 1];
		}
		double mean = sum / intervals.length;
		double varianceSum = 0;
		for (double interval : intervals) {
			double diff = interval - mean;
			varianceSum += diff * diff;
		}
		return Math.sqrt(varianceSum / intervals.length);
	}

	/**
	 * O guarda DETECTA; quem entrega a ocorrencia a moderacao e o motor de risco
	 * ({@code shared/risk/RiskEngine}), que combina a cadencia com os outros sinais, sabe o nivel e
	 * registra a decisao. Antes daqui saia uma denuncia por conta propria — e ela era engolida pela
	 * moderacao como duplicata da do motor, com uma descricao pior (sem o nivel nem o conjunto de
	 * sinais).
	 */
	private void reportIfCooldownExpired(String userId, double stdDev, long now) {
		Long last = lastReported.get(userId);
		if (last != null && now - last < REPORT_COOLDOWN_MS) {
			return;
		}
		lastReported.put(userId, now);
		LOG.warn("Comportamento automatizado detectado para usuario {} (stdDev={}ms)", userId,
			String.format("%.0f", stdDev));
	}
}
