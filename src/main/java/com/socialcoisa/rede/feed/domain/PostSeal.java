package com.socialcoisa.rede.feed.domain;

import java.time.Instant;

/** O selo que uma pessoa deixou num post. Um por pessoa por post (a chave e post_id + user_id). */
public record PostSeal(String id, String postId, String userId, PostSealType seal, Instant createdAt) {

	public static PostSeal create(String id, String postId, String userId, PostSealType seal, Instant now) {
		return new PostSeal(id, postId, userId, seal, now);
	}
}
