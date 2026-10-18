package com.socialcoisa.rede.groups.domain;

/**
 * O estado do grupo — significado operacional, não rótulo de tela.
 *
 * ACTIVE é o normal. RESTRICTED é a medida leve: para de entrar gente, o conteúdo segue. SUSPENDED
 * é a contenção: não entra gente nem conteúdo novo, e o que já existe fica congelado e legível para
 * quem era membro (a equipe da plataforma investiga por um caminho privilegiado, que não é este).
 * CLOSED é o fim: sai da descoberta e o conteúdo deixa de ser acessível.
 *
 * Quem consulta estas respostas é o serviço, em cada operação — a tela nunca decide estado, e um
 * grupo suspenso não aceita publicação por endpoint alternativo só porque a tela não mostrou o
 * botão.
 */
public enum GroupStatus {
	ACTIVE, RESTRICTED, SUSPENDED, CLOSED;

	/** Entrada de novos membros (aberta ou por aprovação). */
	public boolean acceptsNewMembers() {
		return this == ACTIVE;
	}

	/** Publicação e comentário novos. */
	public boolean acceptsNewContent() {
		return this == ACTIVE || this == RESTRICTED;
	}

	/** Aparece na descoberta e na busca — sem revelar nada do conteúdo interno. */
	public boolean visibleInDiscovery() {
		return this == ACTIVE || this == RESTRICTED;
	}

	/** O conteúdo que já existe continua legível para quem era membro. */
	public boolean readableByMembers() {
		return this != CLOSED;
	}
}
