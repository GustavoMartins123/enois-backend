package com.socialcoisa.rede.groups.adapters.out.persistence;

import java.time.Instant;

import com.socialcoisa.rede.feed.domain.ReactionType;
import com.socialcoisa.rede.groups.domain.GroupReaction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "group_post_reactions")
class GroupReactionJpaEntity {

	@Id
	private String id;

	@Column(name = "post_id", nullable = false)
	private String postId;

	@Column(name = "user_id", nullable = false)
	private String userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 24)
	private ReactionType type;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected GroupReactionJpaEntity() {
	}

	static GroupReactionJpaEntity fromDomain(GroupReaction reaction) {
		GroupReactionJpaEntity entity = new GroupReactionJpaEntity();
		entity.id = reaction.id();
		entity.postId = reaction.postId();
		entity.userId = reaction.userId();
		entity.type = reaction.type();
		entity.createdAt = reaction.createdAt();
		return entity;
	}

	GroupReaction toDomain() {
		return new GroupReaction(id, postId, userId, type, createdAt);
	}

	void changeType(ReactionType newType) {
		this.type = newType;
	}
}
