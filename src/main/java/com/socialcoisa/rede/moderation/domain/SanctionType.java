package com.socialcoisa.rede.moderation.domain;

public enum SanctionType {
	RESTRICTION,
	BAN,
	/** Banimento permanente por decisao de admin: como o BAN, e sem caminho de recurso. */
	PERMABAN
}
