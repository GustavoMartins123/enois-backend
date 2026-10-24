package com.socialcoisa.rede.ops.monitoring;

import java.lang.management.ManagementFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Monitorabilidade do painel admin: métricas em memória (séries por minuto), eventos persistidos
 * (erros/falhas/logs) e um buffer dos últimos eventos para a tela em tempo real.
 *
 * <p>Nada aqui bloqueia a requisição: a escrita no banco é assíncrona e em lote; se a fila encher,
 * os eventos mais novos são descartados (contador visível no overview) em vez de derrubar o app.
 * Dados sensíveis são redigidos antes de qualquer persistência.</p>
 */
@Service
public class MonitoringService {

	private static final int RECENT_LIMIT = 500;
	private static final int QUEUE_LIMIT = 5000;
	private static final int MAX_MESSAGE = 1000;
	private static final long MINUTE = 60_000L;

	private final JdbcTemplate jdbc;
	private final Clock clock;
	private final int retentionDays;
	private final int maxEventsPerMinute;
	private final ArrayBlockingQueue<EventRow> queue = new ArrayBlockingQueue<>(QUEUE_LIMIT);
	private final ConcurrentLinkedDeque<EventView> recent = new ConcurrentLinkedDeque<>();
	private final ConcurrentHashMap<Long, MinuteMetrics> minutes = new ConcurrentHashMap<>();
	private final AtomicLong dropped = new AtomicLong();
	private long eventMinute = -1;
	private int eventCount;

	MonitoringService(JdbcTemplate jdbc, Clock clock,
			@Value("${rede.monitoring.retention-days:7}") int retentionDays,
			@Value("${rede.monitoring.max-events-per-minute:1000}") int maxEventsPerMinute) {
		this.jdbc = jdbc;
		this.clock = clock;
		this.retentionDays = Math.max(1, retentionDays);
		this.maxEventsPerMinute = Math.max(1, maxEventsPerMinute);
	}

	// ------------------------------------------------------------------ ingestão

	/** Chamado pelo filtro de requisições: TODA requisição entra nas métricas; 4xx/5xx viram evento. */
	public void recordRequest(String method, String path, String route, int status, long durationMs,
			String requestId, String userId, String clientIp, String deviceId) {
		long bucket = clock.millis() / MINUTE;
		MinuteMetrics metrics = minutes.computeIfAbsent(bucket, key -> new MinuteMetrics());
		metrics.requests.increment();
		if (status >= 500) {
			metrics.errors5xx.increment();
		}
		else if (status >= 400) {
			metrics.errors4xx.increment();
		}
		metrics.durations.addLast(durationMs);
		while (metrics.durations.size() > 2000) {
			metrics.durations.pollFirst();
		}
		metrics.routes.computeIfAbsent(route == null ? "UNMATCHED" : route, key -> new LongAdder()).increment();
		if (status >= 400) {
			String level = status >= 500 ? "ERROR" : "WARN";
			String message = method + " " + (route == null ? path : route) + " -> " + status;
			enqueue(new EventRow(UUID.randomUUID().toString().replace("-", ""), clock.instant(), level, "REQUEST",
				message, requestId, method, path, route, status, durationMs, userId, clientIp, deviceId, null));
		}
	}

	public void recordException(int status, String code, String message, String method, String path,
			String requestId, String userId) {
		enqueue(new EventRow(id(), clock.instant(), status >= 500 ? "ERROR" : "WARN", "EXCEPTION",
			code + ": " + safe(message), requestId, method, path, null, status, null, userId, null, null, null));
	}

	/** Eventos de negócio/segurança: bloqueio, pânico, login recusado, ação de admin etc. */
	public void recordEvent(String level, String type, String message, Map<String, Object> metadata) {
		enqueue(new EventRow(id(), clock.instant(), normalizeLevel(level), normalizeType(type), safe(message),
			null, null, null, null, null, null, null, null, null, metadata == null ? null : metadata.toString()));
	}

	/** Logback: WARN/ERROR viram evento persistido; o requestId/userId vêm do MDC. */
	public void recordLog(String level, String logger, String message, String requestId, String userId, String path) {
		String type = logger != null && logger.equals("audit") ? "AUDIT" : "LOG";
		enqueue(new EventRow(id(), clock.instant(), normalizeLevel(level), type,
			safe((logger == null ? "" : logger + ": ") + message), requestId, null, path, null, null, null,
			userId, null, null, null));
	}

	private synchronized boolean allowEvent() {
		long minute = clock.millis() / MINUTE;
		if (eventMinute != minute) {
			eventMinute = minute;
			eventCount = 0;
		}
		if (eventCount >= maxEventsPerMinute) {
			return false;
		}
		eventCount++;
		return true;
	}

	private void enqueue(EventRow row) {
		// Teto por minuto: um ataque de 404/401 não pode encher o banco de eventos.
		if (!allowEvent()) {
			dropped.incrementAndGet();
			return;
		}
		EventView view = row.view();
		recent.addFirst(view);
		while (recent.size() > RECENT_LIMIT) {
			recent.pollLast();
		}
		if (!queue.offer(row)) {
			dropped.incrementAndGet();
		}
	}

	/** Grava em lote a cada 500 ms: o request não espera o banco. */
	@Scheduled(fixedDelay = 500)
	void flush() {
		List<EventRow> batch = new ArrayList<>(200);
		queue.drainTo(batch, 200);
		if (batch.isEmpty()) {
			return;
		}
		try {
			jdbc.batchUpdate("""
				insert into system_events (id, created_at, level, type, message, request_id, method, path, route,
					status, duration_ms, user_id, client_ip, device_id, metadata)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", batch, 100, (statement, row) -> {
					statement.setString(1, row.id());
					statement.setTimestamp(2, java.sql.Timestamp.from(row.createdAt()));
					statement.setString(3, row.level());
					statement.setString(4, row.type());
					statement.setString(5, row.message());
					statement.setString(6, row.requestId());
					statement.setString(7, row.method());
					statement.setString(8, row.path());
					statement.setString(9, row.route());
					if (row.status() == null) {
						statement.setNull(10, java.sql.Types.INTEGER);
					}
					else {
						statement.setInt(10, row.status());
					}
					if (row.durationMs() == null) {
						statement.setNull(11, java.sql.Types.BIGINT);
					}
					else {
						statement.setLong(11, row.durationMs());
					}
					statement.setString(12, row.userId());
					statement.setString(13, row.clientIp());
					statement.setString(14, row.deviceId());
					statement.setString(15, row.metadata());
				});
		}
		catch (RuntimeException failure) {
			dropped.addAndGet(batch.size());
		}
	}

	/** Sem overview aberto, as séries por minuto também precisam ser podadas. */
	@Scheduled(fixedDelay = 300_000, initialDelay = 300_000)
	void pruneMinutes() {
		long nowBucket = clock.millis() / MINUTE;
		for (Long bucket : minutes.keySet()) {
			if (bucket < nowBucket - 61) {
				minutes.remove(bucket);
			}
		}
	}

	/** Retenção curta: monitorabilidade não é arquivo histórico. */
	@Scheduled(fixedDelay = 3_600_000, initialDelay = 60_000)
	void purgeOld() {
		try {
			jdbc.update("delete from system_events where created_at < ?",
				java.sql.Timestamp.from(clock.instant().minus(retentionDays, ChronoUnit.DAYS)));
		}
		catch (RuntimeException ignored) {
			// Banco indisponível: tenta de novo no próximo ciclo.
		}
	}

	// ------------------------------------------------------------------ leitura

	public OverviewView overview() {
		long nowBucket = clock.millis() / MINUTE;
		for (Long bucket : minutes.keySet()) {
			if (bucket < nowBucket - 61) {
				minutes.remove(bucket);
			}
		}

		List<MinutePoint> series = new ArrayList<>(30);
		long totalRequests = 0;
		long total4xx = 0;
		long total5xx = 0;
		List<Long> p95Samples = new ArrayList<>();
		for (long bucket = nowBucket - 29; bucket <= nowBucket; bucket++) {
			MinuteMetrics metrics = minutes.get(bucket);
			if (metrics == null) {
				series.add(new MinutePoint(Instant.ofEpochMilli(bucket * MINUTE).toString(), 0, 0, 0, 0));
				continue;
			}
			long requests = metrics.requests.sum();
			long e4 = metrics.errors4xx.sum();
			long e5 = metrics.errors5xx.sum();
			totalRequests += requests;
			total4xx += e4;
			total5xx += e5;
			if (bucket >= nowBucket - 4) {
				p95Samples.addAll(metrics.durations);
			}
			series.add(new MinutePoint(Instant.ofEpochMilli(bucket * MINUTE).toString(), requests, e4, e5,
				p95(metrics.durations)));
		}

		long lastMinuteRequests = series.get(series.size() - 1).requests();
		long lastMinute4xx = series.get(series.size() - 1).errors4xx();
		long lastMinute5xx = series.get(series.size() - 1).errors5xx();
		double errorRate = totalRequests == 0 ? 0 : (total4xx + total5xx) * 100.0 / totalRequests;

		Map<String, Long> routes = new LinkedHashMap<>();
		for (long bucket = Math.max(nowBucket - 15, 0); bucket <= nowBucket; bucket++) {
			MinuteMetrics metrics = minutes.get(bucket);
			if (metrics == null) {
				continue;
			}
			metrics.routes.forEach((route, count) -> routes.merge(route, count.sum(), Long::sum));
		}
		List<CountView> topRoutes = routes.entrySet().stream()
			.sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
			.limit(8)
			.map(entry -> new CountView(entry.getKey(), entry.getValue()))
			.toList();

		return new OverviewView(clock.instant().toString(),
			ManagementFactory.getRuntimeMXBean().getUptime() / 1000,
			lastMinuteRequests, lastMinute4xx, lastMinute5xx, errorRate, p95(p95Samples), dropped.get(),
			series, countBy("level"), countBy("type"), topRoutes, recentEvents(100), jvm());
	}

	public List<EventView> events(String level, String type, String q, String before, int limit) {
		StringBuilder sql = new StringBuilder("""
			select id, created_at, level, type, message, request_id, method, path, route, status, duration_ms,
				user_id, client_ip, device_id from system_events where 1=1
			""");
		List<Object> params = new ArrayList<>();
		if (StringUtils.hasText(level)) {
			sql.append(" and level = ?");
			params.add(level.trim().toUpperCase(Locale.ROOT));
		}
		if (StringUtils.hasText(type)) {
			sql.append(" and type = ?");
			params.add(type.trim().toUpperCase(Locale.ROOT));
		}
		String pattern = com.socialcoisa.rede.shared.web.LikePattern.contains(q);
		if (pattern != null) {
			sql.append(" and (lower(message) like ? escape '!' or lower(path) like ? escape '!')");
			params.add(pattern);
			params.add(pattern);
		}
		if (StringUtils.hasText(before)) {
			try {
				sql.append(" and created_at < ?");
				params.add(java.sql.Timestamp.from(Instant.parse(before.trim())));
			}
			catch (RuntimeException ignored) {
				// `before` inválido é ignorado; a lista continua com os mais recentes.
			}
		}
		sql.append(" order by created_at desc limit ?");
		params.add(Math.max(1, Math.min(limit, 200)));
		try {
			return jdbc.query(sql.toString(), (rs, rowNum) -> new EventView(rs.getString(1),
				rs.getTimestamp(2).toInstant().toString(), rs.getString(3), rs.getString(4), rs.getString(5),
				rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9),
				(Integer) rs.getObject(10), (Long) rs.getObject(11), rs.getString(12), rs.getString(13),
				rs.getString(14)), params.toArray());
		}
		catch (RuntimeException failure) {
			return List.of();
		}
	}

	public List<EventView> recentEvents(int limit) {
		return recent.stream().limit(Math.max(1, Math.min(limit, RECENT_LIMIT))).toList();
	}

	private List<CountView> countBy(String column) {
		try {
			return jdbc.query("""
				select %s, count(*) from system_events
				where created_at >= ? group by %s order by count(*) desc limit 12
				""".formatted(column, column), (rs, rowNum) -> new CountView(rs.getString(1), rs.getLong(2)),
				java.sql.Timestamp.from(clock.instant().minus(24, ChronoUnit.HOURS)));
		}
		catch (RuntimeException failure) {
			return List.of();
		}
	}

	private JvmView jvm() {
		Runtime runtime = Runtime.getRuntime();
		long used = runtime.totalMemory() - runtime.freeMemory();
		return new JvmView(used, runtime.maxMemory(), Thread.activeCount(), runtime.availableProcessors());
	}

	private static long p95(Deque<Long> values) {
		if (values == null || values.isEmpty()) {
			return 0;
		}
		List<Long> copy = new ArrayList<>(values);
		copy.sort(Long::compareTo);
		return copy.get(Math.min(copy.size() - 1, (int) Math.ceil(copy.size() * 0.95) - 1));
	}

	private static long p95(List<Long> values) {
		if (values == null || values.isEmpty()) {
			return 0;
		}
		List<Long> copy = new ArrayList<>(values);
		copy.sort(Long::compareTo);
		return copy.get(Math.min(copy.size() - 1, (int) Math.ceil(copy.size() * 0.95) - 1));
	}

	private static String id() {
		return "evt_" + UUID.randomUUID().toString().replace("-", "");
	}

	private static String normalizeLevel(String level) {
		if (!StringUtils.hasText(level)) {
			return "INFO";
		}
		String value = level.trim().toUpperCase(Locale.ROOT);
		return switch (value) {
			case "ERROR", "WARN", "INFO", "DEBUG" -> value;
			default -> "INFO";
		};
	}

	private static String normalizeType(String type) {
		if (!StringUtils.hasText(type)) {
			return "SYSTEM";
		}
		String value = type.trim().toUpperCase(Locale.ROOT);
		return value.length() > 32 ? value.substring(0, 32) : value;
	}

	/** Redige e-mail/token/senha e limita o tamanho antes de gravar/retornar. */
	static String safe(String message) {
		if (message == null) {
			return "";
		}
		String clean = message
			.replaceAll("(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}", "***@***")
			.replaceAll("(?i)(bearer|authorization|token|refresh_token|senha|password)([=: ]+)[^ ,;]+",
				"$1$2***")
			.replaceAll("\\p{Cntrl}", " ");
		return clean.length() > MAX_MESSAGE ? clean.substring(0, MAX_MESSAGE) : clean;
	}

	private record EventRow(String id, Instant createdAt, String level, String type, String message,
			String requestId, String method, String path, String route, Integer status, Long durationMs,
			String userId, String clientIp, String deviceId, String metadata) {
		EventView view() {
			return new EventView(id, createdAt.toString(), level, type, message, requestId, method, path, route,
				status, durationMs, userId, clientIp, deviceId);
		}
	}

	private static final class MinuteMetrics {
		private final LongAdder requests = new LongAdder();
		private final LongAdder errors4xx = new LongAdder();
		private final LongAdder errors5xx = new LongAdder();
		private final Deque<Long> durations = new ConcurrentLinkedDeque<>();
		private final ConcurrentHashMap<String, LongAdder> routes = new ConcurrentHashMap<>();
	}

	public record EventView(String id, String createdAt, String level, String type, String message,
			String requestId, String method, String path, String route, Integer status, Long durationMs,
			String userId, String clientIp, String deviceId) {
	}

	public record MinutePoint(String minute, long requests, long errors4xx, long errors5xx, long p95Ms) {
	}

	public record CountView(String label, long count) {
	}

	public record JvmView(long heapUsedBytes, long heapMaxBytes, int threads, int processors) {
	}

	public record OverviewView(String generatedAt, long uptimeSeconds, long requestsLastMinute,
			long errors4xxLastMinute, long errors5xxLastMinute, double errorRate, long p95Ms, long droppedEvents,
			List<MinutePoint> series, List<CountView> levels24h, List<CountView> types24h,
			List<CountView> topRoutes, List<EventView> recent, JvmView jvm) {
	}
}
