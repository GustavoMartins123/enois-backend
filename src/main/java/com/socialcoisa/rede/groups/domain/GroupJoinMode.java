package com.socialcoisa.rede.groups.domain;

/**
 * Como se entra no grupo.
 *
 * OPEN cria a associação na hora; APPROVAL deixa o pedido pendente até uma decisão de quem
 * administra. Nos dois casos quem entra é conta autenticada e verificada — "aberto" não quer dizer
 * anônimo, e mudar o modo não expulsa quem já está dentro.
 */
public enum GroupJoinMode {
	OPEN, APPROVAL;

	public boolean requiresApproval() {
		return this == APPROVAL;
	}
}
