package com.socialcoisa.rede.feed.adapters.in.web;

import java.util.List;

public record EditCommentRequest(String content, List<String> mediaIds) {
}
