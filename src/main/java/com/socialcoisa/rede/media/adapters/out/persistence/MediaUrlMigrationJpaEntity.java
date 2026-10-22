package com.socialcoisa.rede.media.adapters.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "media_url_migrations")
class MediaUrlMigrationJpaEntity {

	@Id
	@Column(name = "url_hash")
	String urlHash;

	@Column(name = "source_url", nullable = false, length = 1000)
	String sourceUrl;

	@Column(nullable = false, length = 16)
	String status;

	@Column(name = "s3_url", length = 1000)
	String s3Url;

	@Column(nullable = false)
	int attempts;

	@Column(name = "last_error", length = 500)
	String lastError;

	@Column(name = "claimed_at")
	Instant claimedAt;

	@Column(name = "updated_at", nullable = false)
	Instant updatedAt;
}
