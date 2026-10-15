package com.socialcoisa.rede.shared.web;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Acesso direto ao banco dos testes.
 *
 * Existe para os testes montarem o estado que querem observar (ex.: conta ja com e-mail confirmado)
 * sem depender de um token que so existe no e-mail enviado. Nada aqui e usado pela aplicacao.
 */
public final class TestDatabase {

	private static JdbcTemplate jdbcTemplate;

	private TestDatabase() {
	}

	static void install(JdbcTemplate template) {
		jdbcTemplate = template;
	}

	public static void markEmailVerified(String userId, Instant at) {
		require().update("update users set email_verified_at = ? where id = ?", at, userId);
	}

	/** Insere um token de confirmacao com o hash de um valor que o teste conhece. */
	public static void insertVerificationToken(String rawToken, String userId, Instant expiresAt, Instant now) {
		require().update("""
				insert into email_verification_tokens (token, user_id, expires_at, used_at, created_at)
				values (?, ?, ?, null, ?)""", sha256(rawToken), userId, expiresAt, now);
	}

	public static void insertExternalIdentity(String userId, String provider, String externalUserId, String email) {
		require().update("""
				insert into user_external_identities (user_id, provider, external_user_id, email, created_at)
				values (?, ?, ?, ?, ?)""", userId, provider, externalUserId, email, Instant.now());
	}

	/** Registra um pedido de autorizacao OAuth (o callback so aceita state que existe). */
	public static void insertOAuthRequest(String state, String provider, String nonce, Instant expiresAt) {
		require().update("""
				insert into oauth_authorization_requests (state, provider, nonce, user_id, expires_at, created_at)
				values (?, ?, ?, null, ?, ?)""", state, provider, nonce, expiresAt, Instant.now());
	}

	/**
	 * Insere uma vaga direto no banco. Os testes de busca precisam de um acervo controlado e nao
	 * podem passar pelo gate de reputacao (>150) do POST /jobs.
	 */
	public static void insertJob(String id, String title, String company, String location, String type, String level,
			List<String> skills, Instant postedAt) {
		// Cada teste re-semeia o mesmo acervo: limpa antes para o id nao colidir.
		require().update("delete from job_skills where job_id = ?", id);
		require().update("delete from jobs where id = ?", id);
		require().update("""
				insert into jobs (id, title, company, location, type, level, salary, currency, posted_by,
					posted_at, updated_at, community_agreed_at)
				values (?, ?, ?, ?, ?, ?, null, 'BRL', 'user_job_seed', ?, ?, ?)""",
			id, title, company, location, type, level, postedAt, postedAt, postedAt);
		for (String skill : skills) {
			require().update("insert into job_skills (job_id, skill) values (?, ?)", id, skill);
		}
	}

	public static Optional<String> userIdByEmail(String email) {
		return require().query("select id from users where email = ?", (rs, row) -> rs.getString(1), email)
			.stream()
			.findFirst();
	}

	public static String sha256(String value) {
		try {
			byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
				.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			return java.util.HexFormat.of().formatHex(digest);
		}
		catch (java.security.NoSuchAlgorithmException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private static JdbcTemplate require() {
		if (jdbcTemplate == null) {
			throw new IllegalStateException("TestDatabase nao foi inicializado (TestDatabaseSupport ausente).");
		}
		return jdbcTemplate;
	}
}