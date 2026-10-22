package com.socialcoisa.rede.moderation.application.port.out;

import java.util.List;

import com.socialcoisa.rede.shared.risk.RiskAssessment;

/**
 * Le o histórico de risco de uma conta — o que a moderação consulta para responder "por que
 * esta pessoa foi sinalizada?" (o motor de risco grava a avaliação com o nível, os sinais e a
 * versão da regra justamente para a decisão poder ser reconstruída).
 */
public interface RiskAssessmentReader {

	/** As avaliações mais recentes da conta, da mais nova para a mais antiga. */
	List<RiskAssessment> recentFor(String userId, int limit);
}
