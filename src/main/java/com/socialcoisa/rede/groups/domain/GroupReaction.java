package com.socialcoisa.rede.groups.domain;

import java.time.Instant;

import com.socialcoisa.rede.feed.domain.ReactionType;

/**
 * Reação de uma pessoa a uma publicação do grupo: UMA por pessoa por publicação (o banco garante
 * pelo índice único). O catálogo é o mesmo do feed (`ReactionType`), então o seletor do app serve
 * nos dois contextos.
 */
public record GroupReaction(String id, String postId, String userId, ReactionType type, Instant createdAt) {

	public static GroupReaction create(String id, String postId, String userId, ReactionType type, Instant now) {
		return new GroupReaction(id, postId, userId, type, now);
	}
}
