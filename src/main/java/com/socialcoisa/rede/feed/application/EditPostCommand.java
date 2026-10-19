package com.socialcoisa.rede.feed.application;

import java.util.List;

import com.socialcoisa.rede.feed.domain.PostId;
import com.socialcoisa.rede.feed.domain.PostType;
import com.socialcoisa.rede.feed.domain.UserId;

public record EditPostCommand(UserId userId, PostId postId, String content, String pageHtml, String articleBody,
		List<String> tags, PostType type) {

	/** Edicao sem troca de tipo (compatibilidade). */
	public EditPostCommand(UserId userId, PostId postId, String content, String pageHtml, String articleBody,
			List<String> tags) {
		this(userId, postId, content, pageHtml, articleBody, tags, null);
	}
}
