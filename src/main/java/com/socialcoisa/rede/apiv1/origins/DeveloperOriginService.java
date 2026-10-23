package com.socialcoisa.rede.apiv1.origins;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Origens autorizadas a chamar a API pública (/v1) de um navegador.
 *
 * CORS é uma proteção do navegador: sem a origem cadastrada, o preflight não recebe
 * Access-Control-Allow-Origin e o site não consegue ler a resposta. Chamadas servidor-a-servidor
 * (sem Origin) continuam funcionando — CORS não se aplica a elas.
 */
@Service
public class DeveloperOriginService {

	private static final int MAX_PER_USER = 5;
	private static final long CACHE_MS = 30_000;

	private final JdbcTemplate jdbc;
	private final Clock clock;

	private volatile Set<String> cached = Set.of();
	private volatile long cachedAt;

	public DeveloperOriginService(JdbcTemplate jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	public record OriginView(String id, String origin, String createdAt) {
	}

	public List<OriginView> list(String userId) {
		return jdbc.query("""
			select id, origin, created_at from developer_origins
			where user_id = ? order by created_at desc
			""", (rs, rowNum) -> new OriginView(rs.getString(1), rs.getString(2),
				rs.getTimestamp(3).toInstant().toString()), userId);
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
		return new OriginView(id, origin, now.toString());
	}

	public void remove(String userId, String id) {
		int removed = jdbc.update("delete from developer_origins where id = ? and user_id = ?", id, userId);
		if (removed == 0) {
			throw new ApiException(404, "ORIGIN_NOT_FOUND", "Origem nao encontrada.");
		}
		cachedAt = 0;
	}

	/** Todas as origens cadastradas (cache curto: o preflight não pode bater no banco sempre). */
	public Set<String> allowedOrigins() {
		if (System.currentTimeMillis() - cachedAt > CACHE_MS) {
			refresh();
		}
		return cached;
	}

	private synchronized void refresh() {
		try {
			cached = Set.copyOf(jdbc.queryForList("select distinct origin from developer_origins", String.class));
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
