package com.socialcoisa.rede.blog.application;

/**
 * Post de blog publicado (disparado DENTRO da transacao; o fan-out de e-mail escuta depois do
 * commit). Carrega o necessario para montar o e-mail sem tocar em mais nenhum servico.
 */
public record BlogPostPublishedEvent(String blogId, String blogSlug, String blogTitle, String authorUserId,
		String postId, String postTitle, String postBody) {
}
