package com.socialcoisa.rede.games.adapters.out.persistence;

import java.time.Instant;

import com.socialcoisa.rede.games.domain.GameUpdate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "game_updates")
class GameUpdateJpaEntity {

	@Id
	private String id;

	@Column(name = "game_id", nullable = false)
	private String gameId;

	@Column(name = "user_id", nullable = false)
	private String userId;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(length = 4000)
	private String body;

	@Column(length = 40)
	private String version;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected GameUpdateJpaEntity() {
	}

	static GameUpdateJpaEntity fromDomain(GameUpdate update) {
		GameUpdateJpaEntity e = new GameUpdateJpaEntity();
		e.id = update.id();
		e.gameId = update.gameId();
		e.userId = update.userId();
		e.title = update.title();
		e.body = update.body();
		e.version = update.version();
		e.createdAt = update.createdAt();
		return e;
	}

	GameUpdate toDomain() {
		return new GameUpdate(id, gameId, userId, title, body, version, createdAt);
	}
}
