package com.socialcoisa.rede.feed.application;

import java.time.Instant;
import java.util.Optional;

import com.socialcoisa.rede.feed.domain.PostId;
import com.socialcoisa.rede.feed.domain.UserId;

public record GetCommentsQuery(PostId postId, Optional<Instant> before, int limit, Optional<UserId> viewerId) {

	/** Listagem sem sessao (nao ha bloqueio a aplicar). */
	public GetCommentsQuery(PostId postId, Optional<Instant> before, int limit) {
		this(postId, before, limit, Optional.empty());
	}
}