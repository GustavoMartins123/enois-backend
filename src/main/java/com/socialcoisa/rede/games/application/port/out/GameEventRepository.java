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

	/** Compartilhamentos do usuário no jogo desde um instante (o teto diário do G-share). */
	long countByGameAndUserAndKindSince(String gameId, GameEventKind kind, String userId, java.time.Instant since);

	/**
	 * Os eventos desde um instante, para a série diária do painel. A agregação por dia é feita na
	 * aplicação (nada de FUNÇÃO de data do banco: H2 e MariaDB não têm a mesma).
	 *
	 * `limit` corta a leitura: acima de ~50 mil linhas o painel passa a ser uma varredura cara —
	 * o teto é só de leitura, os dados continuam no banco.
	 */
	java.util.List<com.socialcoisa.rede.games.domain.GameEvent> findSince(String gameId, java.time.Instant from,
			int limit);

	/** O jogo foi excluído: o histórico dele vai junto. */
	void deleteByGame(String gameId);
}
