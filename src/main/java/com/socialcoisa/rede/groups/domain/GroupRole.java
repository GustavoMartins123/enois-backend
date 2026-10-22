package com.socialcoisa.rede.groups.domain;

/**
 * O papel de alguém DENTRO de um grupo.
 *
 * OWNER existe uma vez por grupo e é quem responde por ele; ADMIN toca o dia a dia; MODERATOR cuida
 * do conteúdo; MEMBER participa. Papel de grupo não é privilégio de plataforma: nenhum destes
 * alcança função global, e quem não tem papel nenhum é visitante (ver {@link GroupPermissions}).
 */
public enum GroupRole {
	OWNER, ADMIN, MODERATOR, MEMBER
}
