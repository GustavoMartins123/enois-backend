package com.socialcoisa.rede.feed.application.port.out;

import java.util.Set;

/**
 * Autores dos blogs que a pessoa segue.
 *
 * Post de blog nao entra no feed global: ele aparece so no "Para voce" de quem se inscreveu no blog
 * (nunca no "Tudo"). O feed pergunta aqui quem o leitor acompanha e soma esses autores aos amigos.
 */
public interface SubscribedBlogAuthorsReader {

	Set<String> authorsFollowedBy(String userId);
}
