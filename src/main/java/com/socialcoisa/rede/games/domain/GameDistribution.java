package com.socialcoisa.rede.games.domain;

/**
 * Como o jogo é distribuído. `PAID` existe desde já para o preço poder ser modelado, mas a compra
 * está DESLIGADA até as regras comerciais fecharem (ver docs/GAMES.md, seção 7) — a página mostra
 * "em breve" e nada é cobrado.
 */
public enum GameDistribution {
	FREE,
	PAID
}
