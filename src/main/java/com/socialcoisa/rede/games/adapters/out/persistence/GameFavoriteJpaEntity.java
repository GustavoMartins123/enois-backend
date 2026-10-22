package com.socialcoisa.rede.games.adapters.out.persistence;

import java.time.Instant;

import com.socialcoisa.rede.games.domain.GameFavorite;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "game_favorites")
class GameFavoriteJpaEntity {

	@Id
	private String id;

	@Column(name = "user_id", nullable = false)
	private String userId;

	@Column(name = "game_id", nullable = false)
	private String gameId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected GameFavoriteJpaEntity() {
	}

	static GameFavoriteJpaEntity fromDomain(GameFavorite favorite) {
		GameFavoriteJpaEntity e = new GameFavoriteJpaEntity();
		e.id = favorite.id();
		e.userId = favorite.userId();
		e.gameId = favorite.gameId();
		e.createdAt = favorite.createdAt();
		return e;
	}

	GameFavorite toDomain() {
		return new GameFavorite(id, userId, gameId, createdAt);
	}
}
