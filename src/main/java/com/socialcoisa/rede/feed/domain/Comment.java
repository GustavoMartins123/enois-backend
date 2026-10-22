package com.socialcoisa.rede.feed.domain;

import java.time.Instant;
import java.util.List;

public record Comment(String id, PostId postId, UserId authorId, String parentCommentId, String content,
		List<String> mediaIds, Instant createdAt, CommentStatus status, Instant editedAt, boolean pinned) {

	public static Comment create(String id, PostId postId, UserId authorId, String parentCommentId, String content,
			List<String> mediaIds, Instant createdAt) {
		return new Comment(id, postId, authorId, parentCommentId, content,
			mediaIds == null ? List.of() : List.copyOf(mediaIds), createdAt, CommentStatus.ACTIVE, null, false);
	}

	/** Texto e midia novos mantendo autor, post e data original; so o `editedAt` avanca. */
	public Comment edited(String newContent, List<String> newMediaIds, Instant at) {
		return new Comment(id, postId, authorId, parentCommentId, newContent,
			newMediaIds == null ? mediaIds : List.copyOf(newMediaIds), createdAt, status, at, pinned);
	}

	/** Fixa/desafixa o comentário preservando todo o resto. */
	public Comment withPinned(boolean newPinned) {
		return new Comment(id, postId, authorId, parentCommentId, content, mediaIds, createdAt, status, editedAt,
			newPinned);
	}
}
