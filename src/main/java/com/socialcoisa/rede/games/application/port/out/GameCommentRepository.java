package com.socialcoisa.rede.games.application.port.out;

import java.util.List;
import java.util.Optional;

import com.socialcoisa.rede.games.domain.GameComment;

public interface GameCommentRepository {

	GameComment save(GameComment comment);

	Optional<GameComment> findById(String id);

	List<GameComment> findByGame(String gameId, int limit);

	long countByGame(String gameId);

	void delete(String id);

	/** Contagem em lote (para os cards do catálogo): [gameId, count]. */
	List<Object[]> countByGameIds(List<String> gameIds);
}
