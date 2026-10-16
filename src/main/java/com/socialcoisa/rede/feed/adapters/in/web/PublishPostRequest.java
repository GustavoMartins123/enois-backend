package com.socialcoisa.rede.feed.adapters.in.web;

import java.util.List;

import com.socialcoisa.rede.feed.domain.PostType;

public record PublishPostRequest(PostType type, String content, String pageHtml, String articleBody, List<String> tags,
		List<String> mediaIds, String communityId, List<String> mentionedUserIds) {
}
