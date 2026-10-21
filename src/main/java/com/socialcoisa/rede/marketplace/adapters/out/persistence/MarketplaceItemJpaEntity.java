package com.socialcoisa.rede.marketplace.adapters.out.persistence;

import java.time.Instant;

import com.socialcoisa.rede.marketplace.domain.MarketplaceItem;
import com.socialcoisa.rede.marketplace.domain.MarketplaceListing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "marketplace_items")
class MarketplaceItemJpaEntity {

	@Id
	@Column(name = "id", length = 64, nullable = false)
	private String id;

	@Column(name = "seller_id", length = 64, nullable = false)
	private String sellerId;

	@Column(name = "kind", length = 20, nullable = false)
	private String kind;

	@Column(name = "title", length = 160, nullable = false)
	private String title;

	@Column(name = "description", length = 500)
	private String description;

	@Column(name = "content", columnDefinition = "longtext")
	private String content;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected MarketplaceItemJpaEntity() {
	}

	static MarketplaceItemJpaEntity from(MarketplaceItem item) {
		MarketplaceItemJpaEntity e = new MarketplaceItemJpaEntity();
		e.id = item.id();
		e.sellerId = item.sellerId();
		e.kind = item.kind().name();
		e.title = item.title();
		e.description = item.description();
		e.content = item.content();
		e.createdAt = item.createdAt();
		return e;
	}

	MarketplaceItem toDomain() {
		return new MarketplaceItem(id, sellerId, MarketplaceListing.ItemKind.valueOf(kind), title, description,
			content, createdAt);
	}
}
