package com.socialcoisa.rede.marketplace.application.port.out;

import java.util.Optional;

import com.socialcoisa.rede.marketplace.domain.MarketplaceItem;

public interface MarketplaceItemRepository {

	MarketplaceItem save(MarketplaceItem item);

	Optional<MarketplaceItem> findById(String id);
}
