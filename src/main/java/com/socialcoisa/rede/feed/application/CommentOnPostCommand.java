package com.socialcoisa.rede.feed.application;

import java.util.List;

import com.socialcoisa.rede.feed.domain.PostId;
import com.socialcoisa.rede.feed.domain.UserId;

public record CommentOnPostCommand(UserId authorId, PostId postId, String parentCommentId, String content,
		List<String> mediaIds, List<String> mentionedUserIds, boolean mediaSpoiler) {

	public CommentOnPostCommand(UserId authorId, PostId postId, String parentCommentId, String content,
			List<String> mediaIds, List<String> mentionedUserIds) {
		this(authorId, postId, parentCommentId, content, mediaIds, mentionedUserIds, false);
	}
}
