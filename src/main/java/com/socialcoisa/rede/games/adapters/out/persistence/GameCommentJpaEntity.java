package com.socialcoisa.rede.games.adapters.out.persistence;

import java.time.Instant;

import com.socialcoisa.rede.games.domain.GameComment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "game_comments",
	indexes = @Index(name = "idx_game_comments_game", columnList = "game_id, created_at"))
class GameCommentJpaEntity {

	@Id
	private String id;

	@Column(name = "game_id", nullable = false)
	private String gameId;

	@Column(name = "user_id", nullable = false)
	private String userId;

	@Column(nullable = false, length = 2000)
	private String body;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected GameCommentJpaEntity() {
	}

	static GameCommentJpaEntity fromDomain(GameComment comment) {
		GameCommentJpaEntity e = new GameCommentJpaEntity();
		e.id = comment.id();
		e.gameId = comment.gameId();
		e.userId = comment.userId();
		e.body = comment.body();
		e.createdAt = comment.createdAt();
		return e;
	}

	GameComment toDomain() {
		return new GameComment(id, gameId, userId, body, createdAt);
	}
}
