package com.socialcoisa.rede.games.domain;

/**
 * O que o SERVIDOR registrou no jogo — a base das métricas.
 *
 * São dois eventos de propósito: `PAGE_VIEW` é curiosidade (a página abriu) e `PLAY_START` é
 * intenção (alguém começou a jogar). Somar os dois numa "popularidade" só serviria para inflar
 * número; a ordenação do catálogo usa `plays`, que é o segundo.
 */
public enum GameEventKind {
	PAGE_VIEW,
	PLAY_START,
	/** A conquista compartilhada no feed (o postMessage do jogo, com o clique humano). */
	SHARE
}
