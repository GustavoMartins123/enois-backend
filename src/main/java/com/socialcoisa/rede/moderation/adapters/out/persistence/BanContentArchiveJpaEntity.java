package com.socialcoisa.rede.moderation.adapters.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "ban_content_archive")
class BanContentArchiveJpaEntity {

	@Id
	@Column(length = 64)
	private String id;

	@Column(name = "sanction_id", nullable = false, length = 64)
	private String sanctionId;

	@Column(name = "user_id", nullable = false, length = 64)
	private String userId;

	@Column(name = "content_type", nullable = false, length = 32)
	private String contentType;

	@Column(name = "content_id", nullable = false, length = 64)
	private String contentId;

	@Column(name = "content_snapshot", nullable = false, length = 65535)
	private String contentSnapshot;

	@Column(name = "archived_at", nullable = false)
	private Instant archivedAt;

	protected BanContentArchiveJpaEntity() {
	}

	BanContentArchiveJpaEntity(String id, String sanctionId, String userId, String contentType,
			String contentId, String contentSnapshot, Instant archivedAt) {
		this.id = id;
		this.sanctionId = sanctionId;
		this.userId = userId;
		this.contentType = contentType;
		this.contentId = contentId;
		this.contentSnapshot = contentSnapshot;
		this.archivedAt = archivedAt;
	}

	String id() { return id; }
	String sanctionId() { return sanctionId; }
	String userId() { return userId; }
	String contentType() { return contentType; }
	String contentId() { return contentId; }
	String contentSnapshot() { return contentSnapshot; }
	Instant archivedAt() { return archivedAt; }
}
