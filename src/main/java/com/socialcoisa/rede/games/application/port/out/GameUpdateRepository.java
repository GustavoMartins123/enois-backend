package com.socialcoisa.rede.games.application.port.out;

import java.util.List;

import com.socialcoisa.rede.games.domain.GameUpdate;

public interface GameUpdateRepository {

	GameUpdate save(GameUpdate update);

	List<GameUpdate> findByGame(String gameId, int limit);

	/** Ids dos jogos que têm novidade depois de um instante (para o aviso de quem acompanha). */
	long countByGame(String gameId);
}
