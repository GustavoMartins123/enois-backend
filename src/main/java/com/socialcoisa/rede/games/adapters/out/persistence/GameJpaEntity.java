package com.socialcoisa.rede.games.adapters.out.persistence;

import java.time.Instant;

import com.socialcoisa.rede.games.domain.Game;
import com.socialcoisa.rede.games.domain.GameStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "games", indexes = {
		@Index(name = "idx_games_user", columnList = "user_id, slug"),
		@Index(name = "idx_games_status", columnList = "status, submitted_at") })
class GameJpaEntity {

	@Id
	private String id;

	@Column(name = "user_id", nullable = false)
	private String userId;

	@Column(nullable = false, length = 120)
	private String slug;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(length = 4000)
	private String description;

	@Column(length = 500)
	private String cover;

	@Column(nullable = false, length = 500)
	private String url;

	@Column(length = 500)
	private String tags;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private GameStatus status;

	@Column(name = "reviewed_by")
	private String reviewedBy;

	@Column(name = "review_note", length = 500)
	private String reviewNote;

	@Column(name = "submitted_at", nullable = false)
	private Instant submittedAt;

	@Column(name = "reviewed_at")
	private Instant reviewedAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected GameJpaEntity() {
	}

	static GameJpaEntity fromDomain(Game game) {
		GameJpaEntity e = new GameJpaEntity();
		e.id = game.id();
		e.userId = game.userId();
		e.slug = game.slug();
		e.title = game.title();
		e.description = game.description();
		e.cover = game.cover();
		e.url = game.url();
		e.tags = game.tags();
		e.status = game.status();
		e.reviewedBy = game.reviewedBy();
		e.reviewNote = game.reviewNote();
		e.submittedAt = game.submittedAt();
		e.reviewedAt = game.reviewedAt();
		e.createdAt = game.createdAt();
		e.updatedAt = game.updatedAt();
		return e;
	}

	Game toDomain() {
		return Game.restore(id, userId, slug, title, description, cover, url, tags, status, reviewedBy, reviewNote,
			submittedAt, reviewedAt, createdAt, updatedAt);
	}
}
