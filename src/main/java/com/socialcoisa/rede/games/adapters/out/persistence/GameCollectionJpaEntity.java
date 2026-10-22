package com.socialcoisa.rede.games.adapters.out.persistence;

import java.time.Instant;

import com.socialcoisa.rede.games.domain.GameCollection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "game_collections")
class GameCollectionJpaEntity {

	@Id
	private String id;

	@Column(name = "curator_id", nullable = false)
	private String curatorId;

	@Column(nullable = false, length = 160)
	private String title;

	@Column(length = 500)
	private String description;

	@Column(name = "game_ids", columnDefinition = "longtext")
	private String gameIds;

	@Column(nullable = false, length = 16)
	private String status;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	static GameCollectionJpaEntity from(GameCollection c) {
		GameCollectionJpaEntity e = new GameCollectionJpaEntity();
		e.id = c.id();
		e.curatorId = c.curatorId();
		e.title = c.title();
		e.description = c.description();
		e.gameIds = c.idsAsCsv();
		e.status = c.status().name();
		e.createdAt = c.createdAt();
		e.updatedAt = c.updatedAt();
		return e;
	}

	GameCollection toDomain() {
		return new GameCollection(id, curatorId, title, description, GameCollection.idsFromCsv(gameIds),
			GameCollection.Status.valueOf(status), createdAt, updatedAt);
	}
}
