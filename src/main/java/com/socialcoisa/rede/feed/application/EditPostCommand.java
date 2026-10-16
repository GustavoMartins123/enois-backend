package com.socialcoisa.rede.feed.application;

import java.util.List;

import com.socialcoisa.rede.feed.domain.PostId;
import com.socialcoisa.rede.feed.domain.UserId;

public record EditPostCommand(UserId userId, PostId postId, String content, String pageHtml, String articleBody,
		List<String> tags) {
}
