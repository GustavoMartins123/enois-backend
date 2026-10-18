package com.socialcoisa.rede.games.domain;

import java.time.Instant;

/**
 * Jogo do agregador: o Énóis NÃO hospeda — o criador envia o LINK de onde o jogo está
 * publicado e, após revisão, ele roda EMBUTIDO num quadro isolado (sandbox, tela cheia).
 */
public class Game {

	private final String id;
	private final String userId;
	private final String slug;
	private final String title;
	private final String description;
	private final String cover;
	private final String url;
	private final String tags;
	private final GameStatus status;
	private final String reviewedBy;
	private final String reviewNote;
	private final Instant submittedAt;
	private final Instant reviewedAt;
	private final Instant createdAt;
	private final Instant updatedAt;

	private Game(String id, String userId, String slug, String title, String description, String cover, String url,
			String tags, GameStatus status, String reviewedBy, String reviewNote, Instant submittedAt,
			Instant reviewedAt, Instant createdAt, Instant updatedAt) {
		this.id = id;
		this.userId = userId;
		this.slug = slug;
		this.title = title;
		this.description = description;
		this.cover = cover;
		this.url = url;
		this.tags = tags;
		this.status = status;
		this.reviewedBy = reviewedBy;
		this.reviewNote = reviewNote;
		this.submittedAt = submittedAt;
		this.reviewedAt = reviewedAt;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	public static Game submit(String id, String userId, String slug, String title, String description, String cover,
			String url, String tags, Instant now) {
		return new Game(id, userId, slug, title, description, cover, url, tags, GameStatus.SUBMITTED, null, null,
			now, null, now, now);
	}

	public static Game restore(String id, String userId, String slug, String title, String description, String cover,
			String url, String tags, GameStatus status, String reviewedBy, String reviewNote, Instant submittedAt,
			Instant reviewedAt, Instant createdAt, Instant updatedAt) {
		return new Game(id, userId, slug, title, description, cover, url, tags, status, reviewedBy, reviewNote,
			submittedAt, reviewedAt, createdAt, updatedAt);
	}

	public Game approve(String reviewerId, String note, Instant now) {
		return new Game(id, userId, slug, title, description, cover, url, tags, GameStatus.PUBLISHED, reviewerId,
			note, submittedAt, now, createdAt, now);
	}

	public Game reject(String reviewerId, String note, Instant now) {
		return new Game(id, userId, slug, title, description, cover, url, tags, GameStatus.REJECTED, reviewerId,
			note, submittedAt, now, createdAt, now);
	}

	public Game resubmit(Instant now) {
		return new Game(id, userId, slug, title, description, cover, url, tags, GameStatus.SUBMITTED, null, null,
			now, null, createdAt, now);
	}

	public boolean ownedBy(String userId) {
		return this.userId.equals(userId);
	}

	public boolean isPublished() {
		return status == GameStatus.PUBLISHED;
	}

	public String id() { return id; }
	public String userId() { return userId; }
	public String slug() { return slug; }
	public String title() { return title; }
	public String description() { return description; }
	public String cover() { return cover; }
	public String url() { return url; }
	public String tags() { return tags; }
	public GameStatus status() { return status; }
	public String reviewedBy() { return reviewedBy; }
	public String reviewNote() { return reviewNote; }
	public Instant submittedAt() { return submittedAt; }
	public Instant reviewedAt() { return reviewedAt; }
	public Instant createdAt() { return createdAt; }
	public Instant updatedAt() { return updatedAt; }
}
