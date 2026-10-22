package com.socialcoisa.rede.feed.application.port.out;

import java.time.Instant;
import java.util.Optional;

import com.socialcoisa.rede.feed.domain.PostId;

/** Corpo dos artigos (tabela `articles`), separado da linha que o feed le a cada listagem. */
public interface ArticleRepository {

	void save(PostId postId, String body, Instant createdAt);

	void updateBody(PostId postId, String body, Instant editedAt);

	Optional<String> findBody(PostId postId);
}
