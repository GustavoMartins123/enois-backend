package com.socialcoisa.rede.feed.adapters.in.web;

import java.util.List;

public record EditPostRequest(String content, String pageHtml, String articleBody, List<String> tags) {
}
