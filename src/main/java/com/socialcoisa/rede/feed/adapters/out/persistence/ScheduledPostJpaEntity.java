package com.socialcoisa.rede.feed.adapters.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** "tags"/"media_ids" viajam como CSV (convenção das listas no banco, como em games). */
@Entity
@Table(name = "scheduled_posts")
class ScheduledPostJpaEntity {

	@Id
	String id;

	@Column(name = "user_id", nullable = false)
	String userId;

	@Column(name = "post_type", nullable = false)
	String postType;

	@Column(columnDefinition = "text")
	String content;

	@Column(name = "page_html", columnDefinition = "longtext")
	String pageHtml;

	@Column(name = "article_body", columnDefinition = "text")
	String articleBody;

	@Column(columnDefinition = "text")
	String tags;

	@Column(name = "media_ids", columnDefinition = "text")
	String mediaIds;

	@Column(name = "mentioned_ids", columnDefinition = "text")
	String mentionedIds;

	@Column(name = "community_id")
	String communityId;

	@Column(name = "scheduled_at", nullable = false)
	Instant scheduledAt;

	@Column(name = "created_at", nullable = false)
	Instant createdAt;

	@Column(name = "last_error")
	String lastError;

	@Column(nullable = false)
	int attempts;
}
