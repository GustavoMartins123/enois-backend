package com.socialcoisa.rede.communities.adapters.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "favorite_communities",
		uniqueConstraints = @UniqueConstraint(name = "uk_favorite_communities_user_community",
			columnNames = { "user_id", "community_id" }),
		indexes = @Index(name = "idx_favorite_communities_user", columnList = "user_id, created_at"))
class FavoriteCommunityJpaEntity {

	@Id
	private String id;

	@Column(name = "community_id", nullable = false)
	private String communityId;

	@Column(name = "user_id", nullable = false)
	private String userId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected FavoriteCommunityJpaEntity() {
	}

	FavoriteCommunityJpaEntity(String id, String communityId, String userId, Instant createdAt) {
		this.id = id;
		this.communityId = communityId;
		this.userId = userId;
		this.createdAt = createdAt;
	}

	String getCommunityId() { return communityId; }
}
