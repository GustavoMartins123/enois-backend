package com.socialcoisa.rede.apiv1.origins;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Origens autorizadas a chamar a API a partir de um navegador/cliente.
 *
 * As origens do próprio app (allowlist de configuração, inclusive curinga de subdomínio) são
 * "confiáveis" e nunca passam por aqui. Esta tabela guarda apenas origens de terceiros, que se
 * cadastram sozinhas e podem ser bloqueadas automaticamente se gerarem tráfego suspeito.
 */
@Service
public class DeveloperOriginService {

	private static final Logger LOG = LoggerFactory.getLogger(DeveloperOriginService.class);

	private static final int MAX_PER_USER = 5;
	private static final long CACHE_MS = 30_000;

	private final JdbcTemplate jdbc;
	private final Clock clock;

	private volatile Set<String> cached = Set.of();
	private volatile Set<String> cachedBlocked = Set.of();
	private volatile long cachedAt;

	public DeveloperOriginService(JdbcTemplate jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	public record OriginView(String id, String origin, String createdAt, boolean blocked, String blockedReason,
			String blockedUntil) {
	}

	public record AdminOriginView(String id, String origin, String userId, String createdAt, boolean blocked,
			String blockedReason, String blockedUntil, int strikes) {
	}

	public List<OriginView> list(String userId) {
		return jdbc.query("""
			select id, origin, created_at, blocked, blocked_reason, blocked_until from developer_origins
			where user_id = ? order by created_at desc
			""", (rs, rowNum) -> new OriginView(rs.getString(1), rs.getString(2),
				rs.getTimestamp(3).toInstant().toString(), rs.getBoolean(4), rs.getString(5),
				timestamp(rs.getTimestamp(6))), userId);
	}

	public List<AdminOriginView> listAll(int limit) {
		return jdbc.query("""
			select id, origin, user_id, created_at, blocked, blocked_reason, blocked_until, strikes
			from developer_origins order by blocked desc, created_at desc limit ?
			""", (rs, rowNum) -> new AdminOriginView(rs.getString(1), rs.getString(2), rs.getString(3),
				rs.getTimestamp(4).toInstant().toString(), rs.getBoolean(5), rs.getString(6),
				timestamp(rs.getTimestamp(7)), rs.getInt(8)),
			Math.max(1, Math.min(limit, 200)));
	}

	public AdminOriginView byId(String id) {
		return jdbc.query("""
			select id, origin, user_id, created_at, blocked, blocked_reason, blocked_until, strikes
			from developer_origins where id = ?
			""", (rs, rowNum) -> new AdminOriginView(rs.getString(1), rs.getString(2), rs.getString(3),
				rs.getTimestamp(4).toInstant().toString(), rs.getBoolean(5), rs.getString(6),
				timestamp(rs.getTimestamp(7)), rs.getInt(8)), id)
			.stream()
			.findFirst()
			.orElseThrow(() -> new ApiException(404, "ORIGIN_NOT_FOUND", "Origem nao encontrada."));
	}

	private static String timestamp(java.sql.Timestamp value) {
		return value == null ? null : value.toInstant().toString();
	}

	public OriginView add(String userId, String rawOrigin) {
		String origin = normalizeOrigin(rawOrigin);
		Integer count = jdbc.queryForObject("select count(*) from developer_origins where user_id = ?",
			Integer.class, userId);
		if (count != null && count >= MAX_PER_USER) {
			throw new ApiException(400, "TOO_MANY_ORIGINS",
				"Voce pode cadastrar no maximo " + MAX_PER_USER + " origens.");
		}
		String id = "origin_" + UUID.randomUUID().toString().replace("-", "");
		Instant now = clock.instant();
		try {
			jdbc.update("insert into developer_origins (id, user_id, origin, created_at) values (?, ?, ?, ?)", id,
				userId, origin, java.sql.Timestamp.from(now));
		}
		catch (DataIntegrityViolationException duplicate) {
			throw new ApiException(409, "ORIGIN_ALREADY_REGISTERED", "Esta origem ja esta cadastrada.");
		}
		cachedAt = 0;
		return new OriginView(id, origin, now.toString(), false, null, null);
	}

	public void remove(String userId, String id) {
		int removed = jdbc.update("delete from developer_origins where id = ? and user_id = ?", id, userId);
		if (removed == 0) {
			throw new ApiException(404, "ORIGIN_NOT_FOUND", "Origem nao encontrada.");
		}
		cachedAt = 0;
	}

	/** Origens de terceiros ativas (cache curto: o gate não pode bater no banco sempre). */
	public Set<String> allowedOrigins() {
		refreshIfNeeded();
		return cached;
	}

	public boolean isBlocked(String origin) {
		refreshIfNeeded();
		return cachedBlocked.contains(origin);
	}

	/**
	 * Bloqueio automático (guarda de abuso). É TEMPORÁRIO por padrão: um atacante que falsifique
	 * o header Origin não pode desligar a origem de terceiro para sempre. O admin pode bloquear
	 * permanentemente pelo painel.
	 */
	public void block(String origin, String reason, java.time.Duration duration) {
		java.sql.Timestamp until = duration == null ? null : java.sql.Timestamp.from(clock.instant().plus(duration));
		int updated = jdbc.update("""
			update developer_origins set blocked = true, blocked_reason = ?, blocked_at = ?,
				blocked_until = ?, strikes = strikes + 1
			where origin = ? and blocked = false
			""", trimReason(reason), java.sql.Timestamp.from(clock.instant()), until, origin);
		if (updated > 0) {
			LOG.warn("origin_blocked origin={} until={} reason={}", origin, until, trimReason(reason));
		}
		cachedAt = 0;
	}

	/** Libera os bloqueios temporários que já venceram. */
	public int releaseExpired() {
		int released = jdbc.update("""
			update developer_origins set blocked = false, blocked_reason = null, blocked_at = null,
				blocked_until = null
			where blocked = true and blocked_until is not null and blocked_until <= ?
			""", java.sql.Timestamp.from(clock.instant()));
		if (released > 0) {
			LOG.info("origin_block_expired released={}", released);
			cachedAt = 0;
		}
		return released;
	}

	public void blockById(String id, String reason) {
		int updated = jdbc.update("""
			update developer_origins set blocked = true, blocked_reason = ?, blocked_at = ?
			where id = ? and blocked = false
			""", trimReason(reason), java.sql.Timestamp.from(clock.instant()), id);
		if (updated == 0) {
			throw new ApiException(404, "ORIGIN_NOT_FOUND", "Origem ativa nao encontrada.");
		}
		cachedAt = 0;
	}

	public void unblock(String id) {
		int updated = jdbc.update("""
			update developer_origins set blocked = false, blocked_reason = null, blocked_at = null,
				blocked_until = null
			where id = ? and blocked = true
			""", id);
		if (updated == 0) {
			throw new ApiException(404, "ORIGIN_NOT_BLOCKED", "Origem bloqueada nao encontrada.");
		}
		cachedAt = 0;
	}

	private static String trimReason(String reason) {
		if (!StringUtils.hasText(reason)) {
			return "Atividade suspeita";
		}
		String clean = reason.trim();
		return clean.length() > 200 ? clean.substring(0, 200) : clean;
	}

	private void refreshIfNeeded() {
		if (System.currentTimeMillis() - cachedAt > CACHE_MS) {
			refresh();
		}
	}

	private synchronized void refresh() {
		try {
			java.sql.Timestamp now = java.sql.Timestamp.from(clock.instant());
			cached = Set.copyOf(jdbc.queryForList("""
				select distinct origin from developer_origins
				where blocked = false or (blocked_until is not null and blocked_until <= ?)
				""", String.class, now));
			cachedBlocked = Set.copyOf(jdbc.queryForList("""
				select distinct origin from developer_origins
				where blocked = true and (blocked_until is null or blocked_until > ?)
				""", String.class, now));
			cachedAt = System.currentTimeMillis();
		}
		catch (RuntimeException failure) {
			// Banco fora: mantém o último conjunto conhecido.
		}
	}

	/** Normaliza e valida uma origem (esquema, host, porta; sem caminho/query/fragmento). */
	public static String normalizeOrigin(String raw) {
		if (!StringUtils.hasText(raw)) {
			throw new ApiException(400, "INVALID_ORIGIN", "Informe a origem do seu app.");
		}
		String clean = raw.trim();
		if (clean.length() > 200 || clean.equals("*")) {
			throw new ApiException(400, "INVALID_ORIGIN", "Origem invalida.");
		}
		URI uri;
		try {
			uri = URI.create(clean);
		}
		catch (RuntimeException invalid) {
			throw new ApiException(400, "INVALID_ORIGIN", "Origem invalida (use https://seuapp.com).");
		}
		String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
		String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
		boolean local = host.equals("localhost") || host.equals("127.0.0.1") || host.equals("::1");
		if (!"https".equals(scheme) && !(local && "http".equals(scheme))) {
			throw new ApiException(400, "INVALID_ORIGIN",
				"A origem precisa ser https (http so para localhost).");
		}
		if (host.isEmpty() || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
			throw new ApiException(400, "INVALID_ORIGIN", "Origem invalida.");
		}
		String path = uri.getPath();
		if (path != null && !path.isEmpty() && !"/".equals(path)) {
			throw new ApiException(400, "INVALID_ORIGIN", "A origem nao pode ter caminho; use so o dominio.");
		}
		String normalized = scheme + "://" + host + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
		if (normalized.length() > 200) {
			throw new ApiException(400, "INVALID_ORIGIN", "Origem invalida.");
		}
		return normalized;
	}
}
