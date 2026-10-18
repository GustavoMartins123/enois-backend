package com.socialcoisa.rede.games.application.port.out;

import java.util.List;

import com.socialcoisa.rede.games.domain.GameFavorite;

public interface GameFavoriteRepository {

	GameFavorite save(GameFavorite favorite);

	boolean exists(String userId, String gameId);

	int delete(String userId, String gameId);

	long countByGame(String gameId);

	/** Favoritos de uma pessoa, mais recentes primeiro. */
	List<GameFavorite> findByUser(String userId, int limit);
}
