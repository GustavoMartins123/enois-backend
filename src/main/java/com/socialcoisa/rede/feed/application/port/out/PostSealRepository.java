package com.socialcoisa.rede.feed.application.port.out;

import java.util.List;
import java.util.Optional;

import com.socialcoisa.rede.feed.domain.PostSeal;

public interface PostSealRepository {

	Optional<PostSeal> findByPostAndUser(String postId, String userId);

	PostSeal save(PostSeal seal);

	int delete(String postId, String userId);

	/** Selos de varios posts de uma vez — a leitura da pagina nao pode virar uma consulta por post. */
	List<PostSeal> findByPostIds(List<String> postIds);
}
