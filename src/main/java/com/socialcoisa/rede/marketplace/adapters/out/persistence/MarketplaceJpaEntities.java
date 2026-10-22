package com.socialcoisa.rede.marketplace.adapters.out.persistence;

import java.time.Instant;

import com.socialcoisa.rede.marketplace.domain.MarketplaceListing;
import com.socialcoisa.rede.marketplace.domain.MarketplacePurchase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "marketplace_listings")
class MarketplaceListingJpaEntity {

	@Id
	private String id;

	@Column(name = "seller_id", nullable = false)
	private String sellerId;

	@Column(name = "item_kind", nullable = false, length = 20)
	private String itemKind;

	@Column(name = "item_id", nullable = false)
	private String itemId;

	@Column(nullable = false, length = 160)
	private String title;

	@Column(length = 500)
	private String description;

	@Column(nullable = false)
	private long price;

	@Column(nullable = false, length = 16)
	private String status;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	static MarketplaceListingJpaEntity from(MarketplaceListing l) {
		MarketplaceListingJpaEntity e = new MarketplaceListingJpaEntity();
		e.id = l.id();
		e.sellerId = l.sellerId();
		e.itemKind = l.itemKind().name();
		e.itemId = l.itemId();
		e.title = l.title();
		e.description = l.description();
		e.price = l.price();
		e.status = l.status().name();
		e.createdAt = l.createdAt();
		e.updatedAt = l.updatedAt();
		return e;
	}

	MarketplaceListing toDomain() {
		return new MarketplaceListing(id, sellerId, MarketplaceListing.ItemKind.valueOf(itemKind), itemId, title,
			description, price, MarketplaceListing.Status.valueOf(status), createdAt, updatedAt);
	}
}

@Entity
@Table(name = "marketplace_purchases")
class MarketplacePurchaseJpaEntity {

	@Id
	private String id;

	@Column(name = "listing_id", nullable = false)
	private String listingId;

	@Column(name = "buyer_id", nullable = false)
	private String buyerId;

	@Column(name = "seller_id", nullable = false)
	private String sellerId;

	@Column(nullable = false)
	private long price;

	@Column(nullable = false, length = 16)
	private String state;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "decided_at")
	private Instant decidedAt;

	static MarketplacePurchaseJpaEntity from(MarketplacePurchase p) {
		MarketplacePurchaseJpaEntity e = new MarketplacePurchaseJpaEntity();
		e.id = p.id();
		e.listingId = p.listingId();
		e.buyerId = p.buyerId();
		e.sellerId = p.sellerId();
		e.price = p.price();
		e.state = p.state().name();
		e.createdAt = p.createdAt();
		e.decidedAt = p.decidedAt();
		return e;
	}

	MarketplacePurchase toDomain() {
		return new MarketplacePurchase(id, listingId, buyerId, sellerId, price,
			MarketplacePurchase.State.valueOf(state), createdAt, decidedAt);
	}
}
