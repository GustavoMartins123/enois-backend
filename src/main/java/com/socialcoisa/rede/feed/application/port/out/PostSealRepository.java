package com.socialcoisa.rede.feed.application.port.out;

import java.util.List;
import java.util.Optional;

import com.socialcoisa.rede.feed.domain.PostSeal;
import com.socialcoisa.rede.feed.domain.PostSealType;

public interface PostSealRepository {

	/** Os selos desta pessoa neste post (pode haver mais de um). */
	List<PostSeal> findByPostAndUser(String postId, String userId);

	/** O selo exato, para o clique alternar so ele. */
	Optional<PostSeal> findByPostAndUserAndSeal(String postId, String userId, PostSealType seal);

	PostSeal save(PostSeal seal);

	/** Tira um selo (o clique de novo nele). */
	int delete(String postId, String userId, PostSealType seal);

	/** Tira todos os selos desta pessoa neste post ("tirar meus selos"). */
	int deleteAll(String postId, String userId);

	/** Selos de varios posts de uma vez — a leitura da pagina nao pode virar uma consulta por post. */
	List<PostSeal> findByPostIds(List<String> postIds);

	/** Todos os selos de um post, com quem deu cada um (a lista de "quem marcou"). */
	List<PostSeal> findByPostId(String postId);
}
