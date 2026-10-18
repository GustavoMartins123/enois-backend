package com.socialcoisa.rede.games.adapters.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "developer_follows")
class DeveloperFollowJpaEntity {

	@Id
	private String id;

	@Column(name = "follower_id", nullable = false)
	private String followerId;

	@Column(name = "developer_user_id", nullable = false)
	private String developerUserId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected DeveloperFollowJpaEntity() {
	}

	static DeveloperFollowJpaEntity of(String id, String followerId, String developerUserId, Instant now) {
		DeveloperFollowJpaEntity e = new DeveloperFollowJpaEntity();
		e.id = id;
		e.followerId = followerId;
		e.developerUserId = developerUserId;
		e.createdAt = now;
		return e;
	}

	String developerUserId() {
		return developerUserId;
	}
}
