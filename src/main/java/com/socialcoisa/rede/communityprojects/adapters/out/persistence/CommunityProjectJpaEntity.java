package com.socialcoisa.rede.communityprojects.adapters.out.persistence;

import java.time.Instant;

import com.socialcoisa.rede.communityprojects.domain.CommunityProject;
import com.socialcoisa.rede.communityprojects.domain.CommunityProjectStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "community_projects", indexes = {
	@Index(name = "idx_community_projects_user", columnList = "user_id, status"),
	@Index(name = "idx_community_projects_status", columnList = "status, submitted_at") })
class CommunityProjectJpaEntity {

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

	@Column(name = "short_description", length = 300)
	private String shortDescription;

	@Column(length = 500)
	private String cover;

	@Column(length = 500)
	private String url;

	@Column(name = "repo_url", length = 500)
	private String repoUrl;

	@Column(length = 500)
	private String tags;

	@Column(length = 120)
	private String category;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private CommunityProjectStatus status;

	@Column(name = "reviewed_by")
	private String reviewedBy;

	@Column(name = "review_note", length = 500)
	private String reviewNote;

	@Column(name = "submitted_at", nullable = false)
	private Instant submittedAt;

	@Column(name = "reviewed_at")
	private Instant reviewedAt;

	@Column(name = "published_at")
	private Instant publishedAt;

	@Column(name = "suspended_at")
	private Instant suspendedAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected CommunityProjectJpaEntity() {
	}

	static CommunityProjectJpaEntity fromDomain(CommunityProject project) {
		CommunityProjectJpaEntity e = new CommunityProjectJpaEntity();
		e.id = project.id();
		e.userId = project.userId();
		e.slug = project.slug();
		e.title = project.title();
		e.description = project.description();
		e.shortDescription = project.shortDescription();
		e.cover = project.cover();
		e.url = project.url();
		e.repoUrl = project.repoUrl();
		e.tags = project.tags();
		e.category = project.category();
		e.status = project.status();
		e.reviewedBy = project.reviewedBy();
		e.reviewNote = project.reviewNote();
		e.submittedAt = project.submittedAt();
		e.reviewedAt = project.reviewedAt();
		e.publishedAt = project.publishedAt();
		e.suspendedAt = project.suspendedAt();
		e.createdAt = project.createdAt();
		e.updatedAt = project.updatedAt();
		return e;
	}

	CommunityProject toDomain() {
		return CommunityProject.restore(id, userId, slug, title, description, shortDescription, cover, url, repoUrl, tags,
			category, status, reviewedBy, reviewNote, submittedAt, reviewedAt, publishedAt, suspendedAt,
			createdAt, updatedAt);
	}
}
