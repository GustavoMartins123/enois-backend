package com.socialcoisa.rede.games.domain;

/** Proporção do quadro do player — o jogo escolhe como quer aparecer. */
public enum GameAspect {
	WIDESCREEN("16 / 9"),
	CLASSIC("4 / 3"),
	SQUARE("1 / 1"),
	PORTRAIT("9 / 16");

	private final String css;

	GameAspect(String css) {
		this.css = css;
	}

	/** Valor pronto para o `aspect-ratio` do CSS. */
	public String css() {
		return css;
	}
}
