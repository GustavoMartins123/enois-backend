package com.socialcoisa.rede.games.adapters.out.persistence;

import java.time.Instant;

import com.socialcoisa.rede.games.domain.DeveloperProfile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "developer_profiles")
class DeveloperProfileJpaEntity {

	@Id
	@Column(name = "user_id")
	private String userId;

	@Column(name = "display_name", length = 120)
	private String displayName;

	@Column(length = 200)
	private String headline;

	@Column(length = 2000)
	private String bio;

	@Column(length = 500)
	private String links;

	@Column(length = 500)
	private String avatar;

	@Column(length = 500)
	private String cover;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected DeveloperProfileJpaEntity() {
	}

	static DeveloperProfileJpaEntity fromDomain(DeveloperProfile profile) {
		DeveloperProfileJpaEntity e = new DeveloperProfileJpaEntity();
		e.userId = profile.userId();
		e.displayName = profile.displayName();
		e.headline = profile.headline();
		e.bio = profile.bio();
		e.links = profile.links();
		e.avatar = profile.avatar();
		e.cover = profile.cover();
		e.updatedAt = profile.updatedAt();
		return e;
	}

	DeveloperProfile toDomain() {
		return new DeveloperProfile(userId, displayName, headline, bio, links, avatar, cover, updatedAt);
	}
}
