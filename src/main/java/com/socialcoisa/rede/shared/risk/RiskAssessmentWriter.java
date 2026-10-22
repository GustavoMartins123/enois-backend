package com.socialcoisa.rede.shared.risk;

/**
 * Onde a avaliação de risco é guardada.
 *
 * <p>O histórico é o que permite a linha de base (o plano pede número real antes de meta) e a
 * auditoria de uma decisão ("por que esta pessoa foi bloqueada?").
 */
public interface RiskAssessmentWriter {

	/** Grava a avaliação e devolve o registro salvo. */
	RiskAssessment save(RiskAssessment assessment);
}
