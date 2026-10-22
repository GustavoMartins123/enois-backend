package com.socialcoisa.rede.network.domain;

import java.time.Instant;

/**
 * Bloqueio definitivo entre dois usuarios. Quem bloqueia deixa de ver (e de ser visto por) o outro:
 * posts, comentarios, notificacoes, busca e sugestoes.
 */
public class UserBlock {

	private final Long id;
	private final String blockerId;
	private final String blockedId;
	private final Instant createdAt;

	private UserBlock(Long id, String blockerId, String blockedId, Instant createdAt) {
		this.id = id;
		this.blockerId = blockerId;
		this.blockedId = blockedId;
		this.createdAt = createdAt;
	}

	public static UserBlock create(String blockerId, String blockedId, Instant now) {
		return new UserBlock(null, blockerId, blockedId, now);
	}

	public static UserBlock restore(Long id, String blockerId, String blockedId, Instant createdAt) {
		return new UserBlock(id, blockerId, blockedId, createdAt);
	}

	public Long id() {
		return id;
	}

	public String blockerId() {
		return blockerId;
	}

	public String blockedId() {
		return blockedId;
	}

	public Instant createdAt() {
		return createdAt;
	}
}