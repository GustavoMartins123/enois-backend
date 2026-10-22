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

	public static final Set<String> ALL = Set.of("win95", "win98", "win2000", "luna", "aero-glass", "frutiger-aero", "metro", "terminal", "vaporwave", "synthwave", "y2k", "neumorfismo", "blahaj", "intersection", "venus", "aquiles", "twomoons", "silver-jonny", "caravana-do-vento", "bruma-silenciosa", "laboratorio-alaranjado", "vale-esmeralda", "cidade-suspensa", "ilhota-de-tinta", "vagao-noturno", "jardim-de-luz", "brasas-cinzentas", "reino-do-bloco", "cume-celeste", "peste-dourada", "kart-tropical", "estrela-de-papel", "prompt-do-dos", "bancada-amiga", "estacao-preta", "listras-amarelas", "toca-de-palmeira", "tijolo-confiavel", "tela-verde", "papel-e-maquina", "conde-noturno", "norte-frio", "caixa-rustica", "sol-e-sombra", "contraste-puro", "oceano", "floresta", "deserto-ao-entardecer", "aurora", "lavanda", "musgo", "pessego", "grafite", "champanhe", "oliva", "tinta", "papel-kraft", "gelo", "vinho", "madrugada");

	private ThemeIds() {
	}
}
