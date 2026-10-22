package com.socialcoisa.rede.jobs.domain;

/**
 * Estado da candidatura dentro do processo seletivo.
 *
 * As transicoes permitidas ficam AQUI, e nao espalhadas pelo servico: e a regra do dominio, e
 * qualquer caminho novo que mude status passa por ela.
 *
 * Politica:
 * - APPLIED -> IN_REVIEW (o empregador comecou a analisar) ou NOT_SELECTED (descartou de cara);
 * - IN_REVIEW -> ADVANCED (avancou de etapa) ou NOT_SELECTED;
 * - ADVANCED -> IN_REVIEW: permitido, porque o processo pode recuar de etapa;
 * - NOT_SELECTED: terminal. A decisao ja foi comunicada ao candidato e reabrir seria confundi-lo;
 * - repetir o estado atual nao e transicao valida (409 na API): sem isso, cada clique repetido
 *   viraria uma notificacao nova para o candidato.
 */
public enum JobApplicationStatus {

	/** Recebida. */
	APPLIED,

	/** Em analise. */
	IN_REVIEW,

	/** Avancou de etapa. */
	ADVANCED,

	/** Nao selecionada. */
	NOT_SELECTED;

	public boolean canMoveTo(JobApplicationStatus next) {
		if (next == null || next == this) {
			return false;
		}
		return switch (this) {
			case APPLIED -> next == IN_REVIEW || next == NOT_SELECTED;
			case IN_REVIEW -> next == ADVANCED || next == NOT_SELECTED;
			case ADVANCED -> next == IN_REVIEW;
			case NOT_SELECTED -> false;
		};
	}
}
