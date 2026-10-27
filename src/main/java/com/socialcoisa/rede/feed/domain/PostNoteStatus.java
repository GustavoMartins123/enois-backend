package com.socialcoisa.rede.feed.domain;

/** Estado da nota de rodapé: só a aprovada aparece no post; a negada pesa na credibilidade. */
public enum PostNoteStatus {
	PENDING, APPROVED, DENIED
}