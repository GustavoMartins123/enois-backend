package com.socialcoisa.rede.games.adapters.out.persistence;

import java.time.Instant;

import com.socialcoisa.rede.games.domain.GameEventKind;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "game_events")
class GameEventJpaEntity {

	@Id
	private String id;

	@Column(name = "game_id", nullable = false)
	private String gameId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 24)
	private GameEventKind kind;

	@Column(name = "user_id")
	private String userId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected GameEventJpaEntity() {
	}

	static GameEventJpaEntity of(String id, String gameId, GameEventKind kind, String userId, Instant now) {
		GameEventJpaEntity e = new GameEventJpaEntity();
		e.id = id;
		e.gameId = gameId;
		e.kind = kind;
		e.userId = userId;
		e.createdAt = now;
		return e;
	}

	com.socialcoisa.rede.games.domain.GameEvent toDomain() {
		return new com.socialcoisa.rede.games.domain.GameEvent(id, gameId, kind, userId, createdAt);
	}
}
