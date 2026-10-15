package com.socialcoisa.rede.feed.application.port.out;

import java.util.Collection;
import java.util.Map;

/**
 * Leitura da comunidade de um post (nome + se quem esta lendo participa).
 *
 * A porta e declarada aqui, onde e consumida, e implementada pelo modulo de comunidades — mesma
 * inversao usada em {@code ConnectionGraphRepository}.
 */
public interface PostCommunityReader {

	/**
	 * @param viewerId quem esta lendo (nulo quando nao ha sessao): define o campo `joined`
	 * @return comunidade por id, so para os ids informados
	 */
	Map<String, CommunityRef> byIds(Collection<String> communityIds, String viewerId);

	record CommunityRef(String id, String name, boolean joined) {
	}

	static PostCommunityReader none() {
		return (communityIds, viewerId) -> Map.of();
	}
}
