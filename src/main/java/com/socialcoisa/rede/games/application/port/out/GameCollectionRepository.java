package com.socialcoisa.rede.games.application.port.out;

import java.util.List;
import java.util.Optional;

import com.socialcoisa.rede.games.domain.GameCollection;

public interface GameCollectionRepository {

	GameCollection save(GameCollection collection);

	Optional<GameCollection> findById(String id);

	/** Publicadas, mais recentes primeiro. */
	List<GameCollection> findPublished(int limit);

	List<GameCollection> findByCurator(String curatorId);
}
