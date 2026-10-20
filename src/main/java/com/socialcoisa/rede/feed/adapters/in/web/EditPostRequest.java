package com.socialcoisa.rede.feed.adapters.in.web;

import java.util.List;

/**
 * `type` nulo = mantem o tipo atual; preenchido = troca a categoria (mesmas regras da criacao).
 * `topic` nulo/vazio = mantem o assunto atual; preenchido = troca (só em post avulso — o de
 * comunidade herda o dela e a tentativa é recusada).
 */
public record EditPostRequest(String content, String pageHtml, String articleBody, List<String> tags,
		String type, String topic) {
}
