package com.socialcoisa.rede.games.domain;

/**
 * Em que pé o jogo está — o que a página mostra ANTES de a pessoa clicar em jogar.
 *
 * É separado do `GameStatus` de propósito: `GameStatus` é o ciclo de vida na plataforma
 * (enviado/revisado/publicado/recusado) e este é o estado do PROJETO, que o criador declara.
 * Um jogo publicado pode estar em desenvolvimento (acesso antecipado) sem nenhuma contradição.
 */
public enum GameStage {
	IN_DEVELOPMENT,
	DEMO,
	EARLY_ACCESS,
	RELEASED,
	PAUSED
}
