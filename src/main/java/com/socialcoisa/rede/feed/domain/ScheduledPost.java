package com.socialcoisa.rede.feed.domain;

import java.time.Instant;
import java.util.List;

/**
 * Uma publicação que ainda NÃO existe no feed: fica guardada até a hora marcada e o worker a
 * publica pelo mesmo caso de uso do composer. Se a publicação for recusada (conta restrita,
 * conteúdo inválido), o motivo viaja em {@code lastError} e a linha para de ser tentada.
 */
public record ScheduledPost(String id, String userId, PostType type, String content, String pageHtml,
		String articleBody, List<String> tags, List<String> mediaIds, String communityId, Instant scheduledAt,
		Instant createdAt, String lastError, int attempts) {

	public ScheduledPost failed(String error) {
		return new ScheduledPost(id, userId, type, content, pageHtml, articleBody, tags, mediaIds, communityId,
			scheduledAt, createdAt, error, attempts + 1);
	}
}
