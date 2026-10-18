package com.socialcoisa.rede.games.application.port.out;

import java.util.List;
import java.util.Optional;

import com.socialcoisa.rede.games.domain.Game;
import com.socialcoisa.rede.games.domain.GameStatus;

public interface GameRepository {

	Game save(Game game);

	Optional<Game> findById(String id);

	Optional<Game> findByUserAndSlug(String userId, String slug);

	List<Game> findByUser(String userId);

	List<Game> findByUserAndStatus(String userId, GameStatus status, int limit);

	List<Game> findByStatus(GameStatus status, int limit);

	/** Catálogo público: publicados, mais recentes primeiro. */
	List<Game> findPublished(int limit);

	boolean existsByUserAndSlug(String userId, String slug);
}
