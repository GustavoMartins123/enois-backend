package com.socialcoisa.rede.feed.application;

import java.time.Instant;
import java.util.List;

import com.socialcoisa.rede.feed.domain.Comment;
import com.socialcoisa.rede.feed.domain.ReactionType;

public record CommentView(String id, String postId, String authorId, String authorName, String authorUsername,
		String authorAvatar, String authorBadge, String parentCommentId, String content, List<String> mediaIds,
		Instant createdAt, Instant editedAt, ReactionType currentUserReaction) {

	public static CommentView from(Comment comment) {
		return new CommentView(comment.id(), comment.postId().value(), comment.authorId().value(),
			null, null, null, null, comment.parentCommentId(), comment.content(), comment.mediaIds(),
			comment.createdAt(), comment.editedAt(), null);
	}

	/** `authorUsername` deixa o nome e a foto do comentario abrirem o perfil de quem comentou. */
	public static CommentView from(Comment comment, String authorName, String authorUsername, String authorAvatar,
			String authorBadge) {
		return new CommentView(comment.id(), comment.postId().value(), comment.authorId().value(),
			authorName, authorUsername, authorAvatar, authorBadge, comment.parentCommentId(), comment.content(),
			comment.mediaIds(), comment.createdAt(), comment.editedAt(), null);
	}

	/** Anexa a reacao do proprio leitor (o front precisa dela para nao "perder" a reacao ao recarregar). */
	public CommentView withCurrentUserReaction(ReactionType reaction) {
		return new CommentView(id, postId, authorId, authorName, authorUsername, authorAvatar, authorBadge,
			parentCommentId, content, mediaIds, createdAt, editedAt, reaction);
	}
}
