package com.socialcoisa.rede.shared.risk;

/**
 * A resposta do motor de risco — a escada do plano (item 7).
 *
 * <p>Os dois primeiros degraus são inofensivos (registrar e observar); o terceiro abre uma
 * ocorrência para a moderação (quem decide é uma pessoa); o último restringe a operação e só entra
 * em vigor quando o enforcement é ligado, porque um bloqueio por pontuação pode atingir gente
 * honesta.
 */
public enum RiskDecision {

	/** Deixa passar, sem registro (abaixo de qualquer limiar). */
	ALLOW,

	/** Deixa passar e registra a avaliação (o histórico do risco). */
	MONITOR,

	/** Deixa passar, registra e abre uma ocorrência para a moderação. */
	ESCALATE,

	/** Deixa passar, registra, escala e barra a operação (modo de contenção). */
	RESTRICT
}
