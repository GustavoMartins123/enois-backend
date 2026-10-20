package com.socialcoisa.rede.preferences.domain;

import java.util.List;
import java.util.Set;

/**
 * A taxonomia de assuntos e a lista de módulos da personalização.
 *
 * Os assuntos são os mesmos das comunidades — a única taxonomia real do produto. Os selos
 * ficam de fora de propósito: são sinais editoriais dos leitores (importante, dica,
 * massante...), não assuntos. Os módulos são as áreas que o usuário pode desativar na
 * própria experiência; EVENTS também controla o "Acontecendo agora" (dependência do plano).
 */
public final class ContentTaxonomy {

	/** Assuntos válidos para "O que quero ver" e "Assuntos que não gosto". */
	public static final List<String> TOPICS = List.of(
		"TECNOLOGIA", "ENGENHARIA", "DESIGN", "PRODUTO", "LIDERANCA", "CARREIRA", "FREELANCE", "OUTROS");

	/** Módulos desativáveis em "O que não quero ver". */
	public static final List<String> MODULES = List.of(
		"COMMUNITIES", "GROUPS", "MESSAGES", "BLOGS", "JOBS", "COMPANIES",
		"MENTORING", "EVENTS", "DAILY_GAMES", "COMMUNITY_GAMES");

	public static final Set<String> TOPIC_SET = Set.copyOf(TOPICS);
	public static final Set<String> MODULE_SET = Set.copyOf(MODULES);

	private ContentTaxonomy() {
	}
}
