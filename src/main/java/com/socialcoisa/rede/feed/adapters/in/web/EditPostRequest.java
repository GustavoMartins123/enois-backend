package com.socialcoisa.rede.feed.adapters.in.web;

import java.util.List;

/** `type` nulo = mantem o tipo atual; preenchido = troca a categoria (mesmas regras da criacao). */
public record EditPostRequest(String content, String pageHtml, String articleBody, List<String> tags,
		String type) {
}
