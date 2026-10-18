package com.socialcoisa.rede.games.adapters.out.persistence;

import java.time.Instant;

import com.socialcoisa.rede.games.domain.Game;
import com.socialcoisa.rede.games.domain.GameAspect;
import com.socialcoisa.rede.games.domain.GameDetails;
import com.socialcoisa.rede.games.domain.GameDistribution;
import com.socialcoisa.rede.games.domain.GameStage;
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

	@Column(name = "short_description", length = 300)
	private String shortDescription;

	@Column(length = 300)
	private String genres;

	@Column(length = 200)
	private String languages;

	@Column(length = 500)
	private String controls;

	@Column(length = 500)
	private String requirements;

	@Column(name = "content_warnings", length = 300)
	private String contentWarnings;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 24)
	private GameStage stage = GameStage.RELEASED;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private GameDistribution distribution = GameDistribution.FREE;

	@Column(name = "price_coins", nullable = false)
	private int priceCoins;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private GameAspect aspect = GameAspect.WIDESCREEN;

	@Column(length = 40)
	private String version;

	@Column(name = "website_url", length = 500)
	private String websiteUrl;

	@Column(name = "support_url", length = 500)
	private String supportUrl;

	@Column(name = "media_ids", length = 1000)
	private String mediaIds;

	@Column(nullable = false)
	private int plays;

	@Column(name = "published_at")
	private Instant publishedAt;

	@Column(name = "suspended_at")
	private Instant suspendedAt;

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
		GameDetails details = game.details();
		e.shortDescription = details.shortDescription();
		e.genres = details.genres();
		e.languages = details.languages();
		e.controls = details.controls();
		e.requirements = details.requirements();
		e.contentWarnings = details.contentWarnings();
		e.stage = details.stage();
		e.distribution = details.distribution();
		e.priceCoins = details.priceCoins();
		e.aspect = details.aspect();
		e.version = details.version();
		e.websiteUrl = details.websiteUrl();
		e.supportUrl = details.supportUrl();
		e.mediaIds = details.mediaIds();
		e.plays = game.plays();
		e.publishedAt = game.publishedAt();
		e.suspendedAt = game.suspendedAt();
		return e;
	}

	Game toDomain() {
		return Game.restore(id, userId, slug, title, description, cover, url, tags, status, reviewedBy, reviewNote,
			submittedAt, reviewedAt, createdAt, updatedAt,
			GameDetails.of(shortDescription, genres, languages, controls, requirements, contentWarnings, stage,
				distribution, priceCoins, aspect, version, websiteUrl, supportUrl, mediaIds),
			plays, publishedAt, suspendedAt);
	}
}
