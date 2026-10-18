package com.socialcoisa.rede.games.application.port.out;

import com.socialcoisa.rede.games.domain.GameEventKind;

/**
 * O que o servidor registrou de um jogo. Escrever é append-only; ler é só contagem (nada de
 * reconstruir a lista de eventos, que cresceria sem limite).
 */
public interface GameEventRepository {

	void record(String id, String gameId, GameEventKind kind, String userId, java.time.Instant now);

	long countByGameAndKind(String gameId, GameEventKind kind);

	/** Jogadores distintos estimados: ids diferentes que deram PLAY_START. */
	long distinctPlayers(String gameId);
}
