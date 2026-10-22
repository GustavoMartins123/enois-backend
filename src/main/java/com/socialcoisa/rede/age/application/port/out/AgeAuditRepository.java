package com.socialcoisa.rede.age.application.port.out;

/**
 * Trilha de auditoria da classificacao etaria e da supervisao parental.
 *
 * Grava so o essencial (quem, o que, quando e um detalhe curto). Nunca inclui data de nascimento,
 * e-mail do responsavel em claro ou token.
 */
public interface AgeAuditRepository {

	void record(String userId, String event, String actorId, String detail);
}
