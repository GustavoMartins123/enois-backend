package com.socialcoisa.rede.identity.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Apaga os cadastros PENDENTES vencidos.
 *
 * Cada tentativa de cadastro grava uma linha em {@code pending_signups} que só sai quando o
 * e-mail é confirmado. Num ataque em massa de contas (22/10) isso vira lixo permanente e enche o
 * banco — nenhuma dessas linhas serve depois do prazo, então o certo é descartar.
 */
@Component
public class PendingSignupCleanupWorker {

	private static final Logger LOG = LoggerFactory.getLogger(PendingSignupCleanupWorker.class);

	private final JdbcTemplate jdbc;
	private final Clock clock;
	private final boolean enabled;

	public PendingSignupCleanupWorker(JdbcTemplate jdbc, Clock clock,
			@Value("${rede.auth.pending-signup-cleanup-enabled:true}") boolean enabled) {
		this.jdbc = jdbc;
		this.clock = clock;
		this.enabled = enabled;
	}

	@Scheduled(initialDelayString = "${rede.auth.pending-signup-cleanup-initial-delay-ms:60000}",
			fixedDelayString = "${rede.auth.pending-signup-cleanup-interval-ms:900000}")
	public void run() {
		if (!enabled) {
			return;
		}
		try {
			int deleted = purge();
			if (deleted > 0) {
				LOG.info("Cadastros pendentes vencidos apagados: {}", deleted);
			}
		}
		catch (RuntimeException failure) {
			LOG.warn("Falha ao limpar cadastros pendentes: {}", failure.getMessage());
		}
	}

	/** Devolve quantas linhas saíram (usado no teste e no log). */
	public int purge() {
		return jdbc.update("delete from pending_signups where expires_at < ?",
			Timestamp.from(Instant.now(clock)));
	}
}
