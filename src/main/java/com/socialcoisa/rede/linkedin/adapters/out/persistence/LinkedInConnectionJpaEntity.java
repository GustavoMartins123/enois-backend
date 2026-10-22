package com.socialcoisa.rede.linkedin.adapters.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.socialcoisa.rede.linkedin.domain.LinkedInConnection;

@Entity
@Table(name = "linkedin_connections")
class LinkedInConnectionJpaEntity {

	@Id
	@Column(name = "user_id", nullable = false)
	private String userId;

	@Column(name = "member_sub", nullable = false)
	private String memberSub;

	@Column(name = "access_token_enc", nullable = false, columnDefinition = "text")
	private String accessTokenEnc;

	@Column(name = "token_expires_at", nullable = false)
	private Instant tokenExpiresAt;

	@Column(name = "post_sync", nullable = false)
	private boolean postSync;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "last_share_status")
	private String lastShareStatus;

	@Column(name = "last_share_at")
	private Instant lastShareAt;

	protected LinkedInConnectionJpaEntity() {
	}

	static LinkedInConnectionJpaEntity fromDomain(LinkedInConnection connection) {
		LinkedInConnectionJpaEntity entity = new LinkedInConnectionJpaEntity();
		entity.userId = connection.userId();
		entity.memberSub = connection.memberSub();
		entity.accessTokenEnc = connection.accessTokenEnc();
		entity.tokenExpiresAt = connection.tokenExpiresAt();
		entity.postSync = connection.postSync();
		entity.createdAt = connection.createdAt();
		entity.updatedAt = connection.updatedAt();
		entity.lastShareStatus = connection.lastShareStatus();
		entity.lastShareAt = connection.lastShareAt();
		return entity;
	}

	LinkedInConnection toDomain() {
		return new LinkedInConnection(userId, memberSub, accessTokenEnc, tokenExpiresAt, postSync, createdAt,
			updatedAt, lastShareStatus, lastShareAt);
	}
}
