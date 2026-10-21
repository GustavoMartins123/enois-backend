package com.socialcoisa.rede.communityprojects.domain;

import java.time.Instant;

/**
 * Projeto da comunidade: vitrine de algo que a pessoa construiu (app, site, pesquisa, arte...).
 * Não é jogo e não usa a tabela de jogos — tem ciclo de vida e endereço próprios.
 */
public class CommunityProject {

	private final String id;
	private final String userId;
	private final String slug;
	private final String title;
	private final String description;
	private final String shortDescription;
	private final String cover;
	private final String url;
	private final String repoUrl;
	private final String tags;
	private final String category;
	private final CommunityProjectStatus status;
	private final String reviewedBy;
	private final String reviewNote;
	private final Instant submittedAt;
	private final Instant reviewedAt;
	private final Instant publishedAt;
	private final Instant suspendedAt;
	private final Instant createdAt;
	private final Instant updatedAt;

	private CommunityProject(String id, String userId, String slug, String title, String description, String shortDescription,
			String cover, String url, String repoUrl, String tags, String category, CommunityProjectStatus status,
			String reviewedBy, String reviewNote, Instant submittedAt, Instant reviewedAt, Instant publishedAt,
			Instant suspendedAt, Instant createdAt, Instant updatedAt) {
		this.id = id;
		this.userId = userId;
		this.slug = slug;
		this.title = title;
		this.description = description;
		this.shortDescription = shortDescription;
		this.cover = cover;
		this.url = url;
		this.repoUrl = repoUrl;
		this.tags = tags;
		this.category = category;
		this.status = status;
		this.reviewedBy = reviewedBy;
		this.reviewNote = reviewNote;
		this.submittedAt = submittedAt;
		this.reviewedAt = reviewedAt;
		this.publishedAt = publishedAt;
		this.suspendedAt = suspendedAt;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	public static CommunityProject submit(String id, String userId, String slug, String title, String description,
			String shortDescription, String cover, String url, String repoUrl, String tags, String category,
			Instant now) {
		return new CommunityProject(id, userId, slug, title, description, shortDescription, cover, url, repoUrl, tags,
			category, CommunityProjectStatus.SUBMITTED, null, null, now, null, null, null, now, now);
	}

	public static CommunityProject restore(String id, String userId, String slug, String title, String description,
			String shortDescription, String cover, String url, String repoUrl, String tags, String category,
			CommunityProjectStatus status, String reviewedBy, String reviewNote, Instant submittedAt, Instant reviewedAt,
			Instant publishedAt, Instant suspendedAt, Instant createdAt, Instant updatedAt) {
		return new CommunityProject(id, userId, slug, title, description, shortDescription, cover, url, repoUrl, tags,
			category, status, reviewedBy, reviewNote, submittedAt, reviewedAt, publishedAt, suspendedAt,
			createdAt, updatedAt);
	}

	public CommunityProject approve(String reviewerId, String note, Instant now) {
		return new CommunityProject(id, userId, slug, title, description, shortDescription, cover, url, repoUrl, tags,
			category, CommunityProjectStatus.PUBLISHED, reviewerId, note, submittedAt, now,
			publishedAt == null ? now : publishedAt, null, createdAt, now);
	}

	public CommunityProject reject(String reviewerId, String note, Instant now) {
		return new CommunityProject(id, userId, slug, title, description, shortDescription, cover, url, repoUrl, tags,
			category, CommunityProjectStatus.REJECTED, reviewerId, note, submittedAt, now, publishedAt, suspendedAt,
			createdAt, now);
	}

	public CommunityProject resubmit(Instant now) {
		return new CommunityProject(id, userId, slug, title, description, shortDescription, cover, url, repoUrl, tags,
			category, CommunityProjectStatus.SUBMITTED, null, null, now, null, publishedAt, suspendedAt, createdAt, now);
	}

	public CommunityProject edit(String title, String description, String shortDescription, String cover, String url,
			String repoUrl, String tags, String category, String newSlug, Instant now) {
		return new CommunityProject(id, userId, newSlug == null || newSlug.isBlank() ? slug : newSlug, title, description,
			shortDescription, cover, url, repoUrl, tags, category, status, reviewedBy, reviewNote, submittedAt,
			reviewedAt, publishedAt, suspendedAt, createdAt, now);
	}

	public CommunityProject suspend(String reviewerId, String note, Instant now) {
		return new CommunityProject(id, userId, slug, title, description, shortDescription, cover, url, repoUrl, tags,
			category, CommunityProjectStatus.SUSPENDED, reviewerId, note, submittedAt, now, publishedAt, now,
			createdAt, now);
	}

	public CommunityProject restoreFromSuspension(String reviewerId, Instant now) {
		return new CommunityProject(id, userId, slug, title, description, shortDescription, cover, url, repoUrl, tags,
			category, CommunityProjectStatus.PUBLISHED, reviewerId, reviewNote, submittedAt, now, publishedAt, null,
			createdAt, now);
	}

	public boolean ownedBy(String userId) {
		return this.userId.equals(userId);
	}

	public boolean isPublished() {
		return status == CommunityProjectStatus.PUBLISHED;
	}

	public String id() { return id; }
	public String userId() { return userId; }
	public String slug() { return slug; }
	public String title() { return title; }
	public String description() { return description; }
	public String shortDescription() { return shortDescription; }
	public String cover() { return cover; }
	public String url() { return url; }
	public String repoUrl() { return repoUrl; }
	public String tags() { return tags; }
	public String category() { return category; }
	public CommunityProjectStatus status() { return status; }
	public String reviewedBy() { return reviewedBy; }
	public String reviewNote() { return reviewNote; }
	public Instant submittedAt() { return submittedAt; }
	public Instant reviewedAt() { return reviewedAt; }
	public Instant publishedAt() { return publishedAt; }
	public Instant suspendedAt() { return suspendedAt; }
	public Instant createdAt() { return createdAt; }
	public Instant updatedAt() { return updatedAt; }
}
