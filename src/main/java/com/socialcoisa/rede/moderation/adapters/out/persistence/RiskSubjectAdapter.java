package com.socialcoisa.rede.moderation.adapters.out.persistence;

import java.time.Instant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.socialcoisa.rede.moderation.application.port.out.SanctionRepository;
import com.socialcoisa.rede.shared.risk.RiskSubjectReader;

/**
 * Responde ao motor de risco o que ele precisa saber sobre a conta: quando ela nasceu e se tem
 * sanção ativa.
 *
 * <p>Fica na moderação (e não no `shared`) porque é aqui que estão as duas fontes: o cadastro e as
 * sanções. O motor de risco, no `shared`, só conhece a porta.
 */
@Component
class RiskSubjectAdapter implements RiskSubjectReader {

	private static final String SQL = "select created_at from users where id = ?";

	private final JdbcTemplate jdbc;
	private final SanctionRepository sanctions;

	RiskSubjectAdapter(JdbcTemplate jdbc, SanctionRepository sanctions) {
		this.jdbc = jdbc;
		this.sanctions = sanctions;
	}

	@Override
	public Instant createdAtOf(String userId) {
		return jdbc.query(SQL, rs -> rs.next() ? rs.getTimestamp("created_at").toInstant() : null, userId);
	}

	@Override
	public boolean hasActiveSanction(String userId) {
		return sanctions.findActiveByUser(userId).isPresent();
	}
}
