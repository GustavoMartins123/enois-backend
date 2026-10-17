package com.socialcoisa.rede.preferences.domain;

import java.util.Set;

/**
 * Identificadores de tema que o backend aceita — o espelho da coleção do front.
 *
 * O backend não conhece a estética de cada tema (isso é todo CSS no front); aqui
 * vive só a lista do que é válido, para não persistir lixo na conta. Um tema novo
 * no front entra nessa lista no mesmo commit.
 */
public final class ThemeIds {

	public static final Set<String> ALL = Set.of("win95", "win98", "win2000", "luna", "aero-glass",
			"frutiger-aero", "metro", "terminal", "vaporwave", "synthwave", "y2k", "neumorfismo");

	private ThemeIds() {
	}
}
