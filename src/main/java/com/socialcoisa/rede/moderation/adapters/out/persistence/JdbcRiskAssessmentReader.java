package com.socialcoisa.rede.moderation.adapters.out.persistence;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.socialcoisa.rede.moderation.application.port.out.RiskAssessmentReader;
import com.socialcoisa.rede.shared.risk.RiskAssessment;
import com.socialcoisa.rede.shared.risk.RiskDecision;
import com.socialcoisa.rede.shared.risk.RiskLevel;
import com.socialcoisa.rede.shared.risk.RiskSignal;

/**
 * Lê o histórico de risco da conta na tabela {@code risk_assessments}.
 *
 * <p>Os sinais voltam do JSON como lista de códigos do enum: o que a moderação vê é o conjunto de
 * sinais que o motor combinou, e não texto livre.
 */
@Component
class JdbcRiskAssessmentReader implements RiskAssessmentReader {

	private static final String SELECT = """
		select id, user_id, action_type, level, score, signals, rule_version, decision, created_at
		from risk_assessments
		where user_id = ?
		order by created_at desc
		limit ?
		""";

	private final JdbcTemplate jdbc;

	JdbcRiskAssessmentReader(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<RiskAssessment> recentFor(String userId, int limit) {
		return jdbc.query(SELECT, (rs, rowNum) -> new RiskAssessment(rs.getString("id"), rs.getString("user_id"),
			rs.getString("action_type"), RiskLevel.valueOf(rs.getString("level")), rs.getInt("score"),
			parseSignals(rs.getString("signals")), rs.getString("rule_version"),
			RiskDecision.valueOf(rs.getString("decision")), rs.getTimestamp("created_at").toInstant()),
			userId, Math.max(1, limit));
	}

	/** O JSON é uma lista de códigos do enum; sinal desconhecido é ignorado (regra antiga). */
	private List<RiskSignal> parseSignals(String json) {
		List<RiskSignal> sinais = new ArrayList<>();
		if (json == null) {
			return sinais;
		}
		for (String parte : json.replace("[", "").replace("]", "").split(",")) {
			String codigo = parte.trim().replace("\"", "");
			if (codigo.isEmpty()) {
				continue;
			}
			try {
				sinais.add(RiskSignal.valueOf(codigo));
			}
			catch (IllegalArgumentException ignored) {
				// Sinal de uma regra mais nova que este código: não quebra a leitura.
			}
		}
		return sinais;
	}
}
