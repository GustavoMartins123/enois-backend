package com.socialcoisa.rede.moderation.domain;

/** Estado da candidatura a moderador. APPROVED e o que concede o acesso ao painel. */
public enum ModeratorApplicationStatus {
	PENDING,
	APPROVED,
	REJECTED,
	REVOKED
}
