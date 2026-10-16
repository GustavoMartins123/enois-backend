package com.socialcoisa.rede.feed.domain;

public enum PostType {
	INSIGHT,
	ARTICLE,
	QUESTION,
	PROJECT,
	OPPORTUNITY,
	/** Post com uma mini pagina em HTML (colada pelo autor e sanitizada no backend). */
	PAGE
}
