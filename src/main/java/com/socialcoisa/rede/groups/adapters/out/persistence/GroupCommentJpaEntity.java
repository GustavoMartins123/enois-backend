package com.socialcoisa.rede.groups.adapters.out.persistence;

import java.time.Instant;

import com.socialcoisa.rede.groups.domain.GroupComment;
import com.socialcoisa.rede.groups.domain.GroupCommentStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "group_post_comments")
class GroupCommentJpaEntity {

	@Id
	private String id;

	@Column(name = "post_id", nullable = false)
	private String postId;

	@Column(name = "group_id", nullable = false)
	private String groupId;

	@Column(name = "author_id", nullable = false)
	private String authorId;

	@Column(name = "parent_id")
	private String parentId;

	@Column(nullable = false, length = GroupComment.MAX_CONTENT)
	private String content;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private GroupCommentStatus status;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected GroupCommentJpaEntity() {
	}

	static GroupCommentJpaEntity fromDomain(GroupComment comment) {
		GroupCommentJpaEntity entity = new GroupCommentJpaEntity();
		entity.id = comment.id();
		entity.postId = comment.postId();
		entity.groupId = comment.groupId();
		entity.authorId = comment.authorId();
		entity.parentId = comment.parentId();
		entity.content = comment.content();
		entity.status = comment.status();
		entity.createdAt = comment.createdAt();
		entity.updatedAt = comment.updatedAt();
		return entity;
	}

	GroupComment toDomain() {
		return GroupComment.restore(id, postId, groupId, authorId, parentId, content, status, createdAt, updatedAt);
	}
}
