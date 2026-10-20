package com.socialcoisa.rede.games.application.port.out;

import java.util.List;
import java.util.Optional;

import com.socialcoisa.rede.games.domain.GameReview;

public interface GameReviewRepository {

	GameReview save(GameReview review);

	Optional<GameReview> findByGameAndUser(String gameId, String userId);

	List<GameReview> findByGame(String gameId, int limit);

	long countByGame(String gameId);

	double averageByGame(String gameId);

	void delete(String id);
}
