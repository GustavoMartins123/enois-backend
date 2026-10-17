package com.socialcoisa.rede.feed.adapters.out.persistence;

import java.time.Instant;

import com.socialcoisa.rede.feed.domain.PostSeal;
import com.socialcoisa.rede.feed.domain.PostSealType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "post_seals")
class PostSealJpaEntity {

	@Id
	private String id;

	@Column(name = "post_id", nullable = false)
	private String postId;

	@Column(name = "user_id", nullable = false)
	private String userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private PostSealType seal;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected PostSealJpaEntity() {
	}

	static PostSealJpaEntity fromDomain(PostSeal postSeal) {
		PostSealJpaEntity e = new PostSealJpaEntity();
		e.id = postSeal.id();
		e.postId = postSeal.postId();
		e.userId = postSeal.userId();
		e.seal = postSeal.seal();
		e.createdAt = postSeal.createdAt();
		return e;
	}

	PostSeal toDomain() {
		return new PostSeal(id, postId, userId, seal, createdAt);
	}
}
