package com.socialcoisa.rede.games.adapters.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import com.socialcoisa.rede.games.application.port.out.GameReviewRepository;
import com.socialcoisa.rede.games.domain.GameReview;

@Repository
class GameReviewPersistenceAdapter implements GameReviewRepository {

	private final SpringDataGameReviewJpaRepository repository;

	GameReviewPersistenceAdapter(SpringDataGameReviewJpaRepository repository) {
		this.repository = repository;
	}

	@Override
	public GameReview save(GameReview review) {
		return repository.save(GameReviewJpaEntity.fromDomain(review)).toDomain();
	}

	@Override
	public Optional<GameReview> findByGameAndUser(String gameId, String userId) {
		return repository.findByGameIdAndUserId(gameId, userId).map(GameReviewJpaEntity::toDomain);
	}

	@Override
	public List<GameReview> findByGame(String gameId, int limit) {
		return repository.findByGameIdOrderByCreatedAtDesc(gameId, PageRequest.of(0, Math.max(1, limit)))
			.stream().map(GameReviewJpaEntity::toDomain).toList();
	}

	@Override
	public long countByGame(String gameId) {
		return repository.countByGameId(gameId);
	}

	@Override
	public List<Object[]> statsByGameIds(List<String> gameIds) {
		if (gameIds == null || gameIds.isEmpty()) {
			return List.of();
		}
		return repository.statsByGameIds(gameIds);
	}

	@Override
	public void delete(String id) {
		repository.deleteById(id);
	}

	@Override
	public double averageByGame(String gameId) {
		Double average = repository.averageByGameId(gameId);
		return average == null ? 0d : average;
	}
}
