package com.socialcoisa.rede.shared.risk;

import java.time.Instant;
import java.util.List;

/**
 * A avaliação de risco de uma conta num momento do tempo.
 *
 * <p>É o registro que permite reconstruir a decisão: quem foi avaliado, o que foi feito, com quais
 * sinais, em que nível, qual resposta saiu e qual versão da regra estava valendo. Sem isso, a
 * defendida de um bloqueio é um mistério — e o plano mestre é explícito: "qualquer decisão relevante
 * deve registrar os motivos que contribuíram para a classificação, permitindo auditoria
 * posterior" e "garantir que as decisões possam ser reconstruídas durante uma investigação".
 *
 * @param id identificador da avaliação
 * @param userId conta avaliada
 * @param action o que a pessoa estava tentando fazer ("publish", "comment", "message"…)
 * @param level nível de risco apurado
 * @param score soma dos pesos dos sinais
 * @param signals sinais que contribuíram (nunca texto livre: só códigos)
 * @param ruleVersion versão da política que decidiu (para reconstruir o porquê)
 * @param decision resposta aplicada
 * @param createdAt instante da avaliação
 */
public record RiskAssessment(String id, String userId, String action, RiskLevel level, int score,
		List<RiskSignal> signals, String ruleVersion, RiskDecision decision, Instant createdAt) {

	public RiskAssessment {
		signals = List.copyOf(signals);
	}
}
