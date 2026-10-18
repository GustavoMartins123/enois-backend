package com.socialcoisa.rede.games.domain;

/**
 * Ciclo de vida do jogo na plataforma.
 *
 * `SUSPENDED` é a moderação tirando do ar: o jogo some do catálogo e da página pública, mas o
 * registro, o histórico e (quando existir) as licenças continuam no banco — suspender não é apagar.
 */
public enum GameStatus {
	SUBMITTED,
	PUBLISHED,
	REJECTED,
	SUSPENDED
}
