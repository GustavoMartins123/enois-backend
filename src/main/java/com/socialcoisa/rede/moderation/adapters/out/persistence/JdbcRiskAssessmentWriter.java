package com.socialcoisa.rede.moderation.adapters.out.persistence;

import java.sql.Timestamp;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.socialcoisa.rede.shared.risk.RiskAssessment;
import com.socialcoisa.rede.shared.risk.RiskAssessmentWriter;

/**
 * Grava a avaliação de risco na tabela {@code risk_assessments}.
 *
 * <p>JDBC direto (e não JPA) porque a tabela é um histórico que só cresce e é lido por agregação
 * — o mesmo desenho do {@code JdbcModerationLearningAdapter}, que é o outro ledger do sistema de
 * segurança. Os sinais vão como a lista de códigos em JSON: nada de texto livre para escapar.
 */
@Component
class JdbcRiskAssessmentWriter implements RiskAssessmentWriter {

	private static final String INSERT = """
		insert into risk_assessments
			(id, user_id, action_type, level, score, signals, rule_version, decision, created_at)
		values (?, ?, ?, ?, ?, ?, ?, ?, ?)
		""";

	private final JdbcTemplate jdbc;

	JdbcRiskAssessmentWriter(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public RiskAssessment save(RiskAssessment assessment) {
		jdbc.update(INSERT, assessment.id(), assessment.userId(), assessment.action(), assessment.level().name(),
			assessment.score(), toJson(assessment), assessment.ruleVersion(), assessment.decision().name(),
			Timestamp.from(assessment.createdAt()));
		return assessment;
	}

	/** Só códigos conhecidos: a lista de sinais é um array JSON de strings do enum. */
	private String toJson(RiskAssessment assessment) {
		StringBuilder json = new StringBuilder("[");
		for (int i = 0; i < assessment.signals().size(); i++) {
			if (i > 0) {
				json.append(',');
			}
			json.append('"').append(assessment.signals().get(i).name()).append('"');
		}
		return json.append(']').toString();
	}
}
