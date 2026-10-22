package com.socialcoisa.rede.marketplace.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Conteúdo vendido no marketplace fora da galeria de temas: template, banner, página,
 * post, blog ou serviço. O comprador recebe `content` em "Minhas compras" e pode editar
 * os textos antes de copiar o HTML.
 */
public record MarketplaceItem(String id, String sellerId, MarketplaceListing.ItemKind kind, String title,
		String description, String content, Instant createdAt) {

	public static MarketplaceItem create(String sellerId, MarketplaceListing.ItemKind kind, String title,
			String description, String content, Instant now) {
		return new MarketplaceItem("mitem_" + UUID.randomUUID().toString().replace("-", ""), sellerId, kind,
			title == null ? null : title.trim(), description == null ? null : description.trim(),
			content, now);
	}
}
