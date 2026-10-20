package com.socialcoisa.rede.games.adapters.out.persistence;

import java.time.Instant;

import com.socialcoisa.rede.games.domain.GameReview;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "game_reviews",
	uniqueConstraints = @UniqueConstraint(name = "uk_game_review_user", columnNames = { "game_id", "user_id" }),
	indexes = @Index(name = "idx_game_reviews_game", columnList = "game_id, created_at"))
class GameReviewJpaEntity {

	@Id
	private String id;

	@Column(name = "game_id", nullable = false)
	private String gameId;

	@Column(name = "user_id", nullable = false)
	private String userId;

	@Column(nullable = false)
	private int rating;

	@Column(length = 2000)
	private String body;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected GameReviewJpaEntity() {
	}

	static GameReviewJpaEntity fromDomain(GameReview review) {
		GameReviewJpaEntity e = new GameReviewJpaEntity();
		e.id = review.id();
		e.gameId = review.gameId();
		e.userId = review.userId();
		e.rating = review.rating();
		e.body = review.body();
		e.createdAt = review.createdAt();
		e.updatedAt = review.updatedAt();
		return e;
	}

	GameReview toDomain() {
		return new GameReview(id, gameId, userId, rating, body, createdAt, updatedAt);
	}
}
