package com.socialcoisa.rede.jobs.application.port.out;

/**
 * Registro de exclusão de vaga sem dar retorno aos candidatos (item 7 do backlog). A chave
 * é a VAGA: a mesma exclusão registrada duas vezes não conta duas vezes, e vaga respondida
 * (todas as candidaturas fora de APPLIED) nunca é registrada.
 */
public interface JobAbandonPenaltyRepository {

	/** Devolve true quando o registro é NOVO (primeira exclusão daquela vaga). */
	boolean record(String jobId, String userId, int unanswered);

	int countByUser(String userId);
}
