package com.socialcoisa.rede.shared.risk;

/**
 * Os sinais que o motor de risco combina.
 *
 * <p>Cada sinal é observável e determinístico (nada de "parece suspeito"): cadência de robô,
 * conteúdo repetido, conta nova e conta com sanção ativa. O peso de cada um é configurável
 * ({@code rede.risk.weights.*}) e o conjunto de sinais que o nível exige também
 * ({@code rede.risk.thresholds.*}).
 */
public enum RiskSignal {

	/** Intervalos entre as ações quase idênticos: cadência de automação. */
	ROBOTIC_CADENCE,

	/** O mesmo texto publicado pela mesma conta dentro da janela. */
	DUPLICATE_CONTENT,

	/** Conta recém-criada (janela curta, sem histórico). */
	NEW_ACCOUNT,

	/** Conta com sanção ativa (restrição ou banimento). */
	ACTIVE_SANCTION
}
