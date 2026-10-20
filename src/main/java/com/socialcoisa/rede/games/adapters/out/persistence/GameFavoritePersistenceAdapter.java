package com.socialcoisa.rede.games.adapters.out.persistence;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import com.socialcoisa.rede.games.application.port.out.GameFavoriteRepository;
import com.socialcoisa.rede.games.domain.GameFavorite;

@Repository
class GameFavoritePersistenceAdapter implements GameFavoriteRepository {

	private final SpringDataGameFavoriteJpaRepository favorites;

	GameFavoritePersistenceAdapter(SpringDataGameFavoriteJpaRepository favorites) {
		this.favorites = favorites;
	}

	@Override
	public GameFavorite save(GameFavorite favorite) {
		return favorites.save(GameFavoriteJpaEntity.fromDomain(favorite)).toDomain();
	}

	@Override
	public boolean exists(String userId, String gameId) {
		return favorites.existsByUserIdAndGameId(userId, gameId);
	}

	@Override
	public int delete(String userId, String gameId) {
		return favorites.deleteByUserAndGame(userId, gameId);
	}

	@Override
	public long countByGame(String gameId) {
		return favorites.countByGameId(gameId);
	}

	@Override
	public List<GameFavorite> findByUser(String userId, int limit) {
		return favorites.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, Math.max(1, limit))).stream()
			.map(GameFavoriteJpaEntity::toDomain).toList();
	}

	@Override
	public void deleteByGame(String gameId) {
		favorites.deleteByGameId(gameId);
	}
}
