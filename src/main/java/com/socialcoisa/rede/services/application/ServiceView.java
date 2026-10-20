package com.socialcoisa.rede.services.application;

import com.socialcoisa.rede.services.domain.ServiceOffer;

/**
 * A view do serviço. `username` resolve o endereço público (/services/{username}/{slug});
 * `owned` diz se o leitor é o prestador (é o que liga os botões de editar/remover na página).
 */
public record ServiceView(String id, String username, String slug, String title, String description,
		String link, String category, String status, String reviewNote, boolean owned) {

	public static ServiceView of(ServiceOffer offer, String username, String viewerId) {
		return new ServiceView(offer.id(), username, offer.slug(), offer.title(), offer.description(),
			offer.link(), offer.category().name(), offer.status().name(), offer.reviewNote(),
			viewerId != null && offer.ownedBy(viewerId));
	}
}
