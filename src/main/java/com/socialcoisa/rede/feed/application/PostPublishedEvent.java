package com.socialcoisa.rede.feed.application;

/**
 * Um post entrou no ar. O automod de spam escuta DEPOIS do commit (em outra thread): publicar não
 * pode esperar a análise, e a análise não pode enxergar um post que ainda vai sofrer rollback.
 */
public record PostPublishedEvent(String postId, String authorId) {
}
