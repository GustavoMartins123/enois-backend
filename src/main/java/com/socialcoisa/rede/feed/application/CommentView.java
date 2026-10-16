package com.socialcoisa.rede.feed.application;

import java.time.Instant;

import com.socialcoisa.rede.feed.domain.Comment;
import com.socialcoisa.rede.feed.domain.ReactionType;

public record CommentView(String id, String postId, String authorId, String authorName, String authorUsername,
		String authorAvatar, String parentCommentId, String content, Instant createdAt, Instant editedAt,
		ReactionType currentUserReaction) {

	public static CommentView from(Comment comment) {
		return new CommentView(comment.id(), comment.postId().value(), comment.authorId().value(),
			null, null, null, comment.parentCommentId(), comment.content(), comment.createdAt(), comment.editedAt(),
			null);
	}

	/** `authorUsername` deixa o nome e a foto do comentario abrirem o perfil de quem comentou. */
	public static CommentView from(Comment comment, String authorName, String authorUsername, String authorAvatar) {
		return new CommentView(comment.id(), comment.postId().value(), comment.authorId().value(),
			authorName, authorUsername, authorAvatar, comment.parentCommentId(), comment.content(),
			comment.createdAt(), comment.editedAt(), null);
	}

	/** Anexa a reacao do proprio leitor (o front precisa dela para nao "perder" a reacao ao recarregar). */
	public CommentView withCurrentUserReaction(ReactionType reaction) {
		return new CommentView(id, postId, authorId, authorName, authorUsername, authorAvatar, parentCommentId,
			content, createdAt, editedAt, reaction);
	}
}
