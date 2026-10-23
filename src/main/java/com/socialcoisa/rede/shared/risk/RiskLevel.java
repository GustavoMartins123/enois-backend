package com.socialcoisa.rede.shared.risk;

/**
 * Nível de risco de uma conta, pelo conjunto de sinais observados.
 *
 * <p>A escala é do plano mestre de segurança humana (item 7): o risco não é um número solto, é
 * um nível com uma resposta correspondente, e a resposta é sempre proporcional — o motor de
 * banimento permanente não é o motor de risco (quem bane é uma pessoa).
 */
public enum RiskLevel {

	/** Só ruído: nada de mais, a pessoa segue normalmente. */
	LOW,

	/** Algum sinal fraco: vale observar, sem tocar na pessoa. */
	MEDIUM,

	/** Sinais combinados: a conta precisa aparecer para a moderação, que decide. */
	HIGH,

	/** Sinais fortos: a operação merece contenção — e a decisão final é humana. */
	CRITICAL
}
