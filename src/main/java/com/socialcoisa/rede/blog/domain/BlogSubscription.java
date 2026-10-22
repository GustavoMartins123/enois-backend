package com.socialcoisa.rede.blog.domain;

import java.time.Instant;

/** Inscricao num blog: e o que faz as novidades dele aparecerem no "Para voce" de quem segue. */
public record BlogSubscription(String id, String blogId, String userId, Instant createdAt) {

	public static BlogSubscription create(String id, String blogId, String userId, Instant now) {
		return new BlogSubscription(id, blogId, userId, now);
	}
}
