package com.socialcoisa.rede.cosmetics.domain;

/** Ciclo de publicação: um visual nunca entra no catálogo sem passar pela moderação. */
public enum CommunityCosmeticStatus {
	DRAFT,
	PENDING_MODERATION,
	APPROVED,
	PUBLISHED,
	REJECTED,
	HIDDEN,
	REMOVED
}
