package com.socialcoisa.rede.marketplace.application.port.out;

import java.util.List;
import java.util.Optional;

import com.socialcoisa.rede.marketplace.domain.MarketplaceListing;
import com.socialcoisa.rede.marketplace.domain.MarketplacePurchase;

public interface MarketplaceRepository {

	MarketplaceListing saveListing(MarketplaceListing listing);

	Optional<MarketplaceListing> findListingById(String id);

	Optional<MarketplaceListing> findActiveByItem(String itemKind, String itemId);

	List<MarketplaceListing> findActive(int limit);

	/** Todas as listagens (qualquer status), para a limpeza de resquícios. */
	List<MarketplaceListing> findAll(int limit);

	/** Compras paradas no escrow de uma listagem (reembolso na remoção). */
	List<MarketplacePurchase> findHeldByListing(String listingId);

	List<MarketplaceListing> findBySeller(String sellerId);

	MarketplacePurchase savePurchase(MarketplacePurchase purchase);

	Optional<MarketplacePurchase> findPurchaseById(String id);

	List<MarketplacePurchase> findHeldOlderThan(int limit);

	List<MarketplacePurchase> findPurchasesByBuyer(String buyerId);

	List<MarketplacePurchase> findSalesBySeller(String sellerId);
}
