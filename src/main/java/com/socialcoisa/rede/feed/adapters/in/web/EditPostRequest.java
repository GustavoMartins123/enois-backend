package com.socialcoisa.rede.feed.adapters.in.web;

import java.util.List;

public record EditPostRequest(String content, String pageHtml, List<String> tags) {
}
