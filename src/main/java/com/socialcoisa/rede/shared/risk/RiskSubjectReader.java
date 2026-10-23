package com.socialcoisa.rede.shared.risk;

import java.time.Instant;

/**
 * O que o motor de risco precisa saber sobre a conta avaliada, sem depender do módulo que é o
 * dono desses dados.
 *
 * <p>Fica em {@code shared} pelo mesmo motivo do {@link com.socialcoisa.rede.shared.sanctions.SanctionGateReader}:
 * quem avalia o risco (o motor) não pode depender do módulo de moderação nem do de perfil, que são
 * os donos da escrita e do cadastro. A implementação vive na moderação, que enxerga os dois.
 */
public interface RiskSubjectReader {

	/**
	 * Momento em que a conta foi criada (null quando a conta não existe).
	 *
	 * <p>É o que separa "conta nova" de "conta com histórico" — o sinal mais barato de todos: a
	 * data está no próprio cadastro.
	 */
	Instant createdAtOf(String userId);

	/** A conta tem sanção ativa (restrição ou banimento)? */
	boolean hasActiveSanction(String userId);
}
