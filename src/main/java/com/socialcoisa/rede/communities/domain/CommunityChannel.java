package com.socialcoisa.rede.communities.domain;

import java.time.Instant;

/**
 * Canal privado de uma comunidade, separado por tema.
 *
 * O acesso segue a participação da comunidade: quem é membro lê e publica em todos os canais;
 * quem não é não vê nem a lista. Publicações de canal não entram em feed nenhum.
 */
public record CommunityChannel(String id, String communityId, String name, String slug, String topic,
		String description, String createdBy, Instant createdAt) {
}
