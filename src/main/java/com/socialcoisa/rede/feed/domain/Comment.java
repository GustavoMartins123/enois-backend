package com.socialcoisa.rede.feed.domain;

import java.time.Instant;

public record Comment(String id, PostId postId, UserId authorId, String parentCommentId, String content,
		Instant createdAt, CommentStatus status, Instant editedAt) {

	public static Comment create(String id, PostId postId, UserId authorId, String parentCommentId, String content,
			Instant createdAt) {
		return new Comment(id, postId, authorId, parentCommentId, content, createdAt, CommentStatus.ACTIVE, null);
	}

	/** Texto novo mantendo autor, post e data original; so o `editedAt` avanca. */
	public Comment edited(String newContent, Instant at) {
		return new Comment(id, postId, authorId, parentCommentId, newContent, createdAt, status, at);
	}
}
