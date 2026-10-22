package com.socialcoisa.rede.feed.application;

import java.time.Instant;
import java.util.List;

import com.socialcoisa.rede.feed.domain.Comment;
import com.socialcoisa.rede.feed.domain.ReactionType;

public record CommentView(String id, String postId, String authorId, String authorName, String authorUsername,
		String authorAvatar, String authorBadge, List<String> authorBadges, String parentCommentId, String content,
		List<String> mediaIds, Instant createdAt, Instant editedAt, ReactionType currentUserReaction,
		String authorAvatarFrame, int reactionCount, boolean pinned, boolean mediaSpoiler) {

	public static CommentView from(Comment comment) {
		return new CommentView(comment.id(), comment.postId().value(), comment.authorId().value(),
			null, null, null, null, List.of(), comment.parentCommentId(), comment.content(), comment.mediaIds(),
			comment.createdAt(), comment.editedAt(), null, null, 0, comment.pinned(), comment.mediaSpoiler());
	}

	public static CommentView from(Comment comment, String authorName, String authorUsername, String authorAvatar,
			List<String> authorBadges) {
		List<String> badges = authorBadges == null ? List.of() : List.copyOf(authorBadges);
		return new CommentView(comment.id(), comment.postId().value(), comment.authorId().value(),
			authorName, authorUsername, authorAvatar, badges.isEmpty() ? null : badges.get(0), badges,
			comment.parentCommentId(), comment.content(), comment.mediaIds(), comment.createdAt(),
			comment.editedAt(), null, null, 0, comment.pinned(), comment.mediaSpoiler());
	}

	public CommentView withAvatarFrame(String frame) {
		return new CommentView(id, postId, authorId, authorName, authorUsername, authorAvatar, authorBadge,
			authorBadges, parentCommentId, content, mediaIds, createdAt, editedAt, currentUserReaction, frame,
			reactionCount, pinned, mediaSpoiler);
	}

	public CommentView withCurrentUserReaction(ReactionType reaction) {
		return new CommentView(id, postId, authorId, authorName, authorUsername, authorAvatar, authorBadge,
			authorBadges, parentCommentId, content, mediaIds, createdAt, editedAt, reaction, authorAvatarFrame,
			reactionCount, pinned, mediaSpoiler);
	}

	public CommentView withReactionCount(int count) {
		return new CommentView(id, postId, authorId, authorName, authorUsername, authorAvatar, authorBadge,
			authorBadges, parentCommentId, content, mediaIds, createdAt, editedAt, currentUserReaction,
			authorAvatarFrame, count, pinned, mediaSpoiler);
	}
}
