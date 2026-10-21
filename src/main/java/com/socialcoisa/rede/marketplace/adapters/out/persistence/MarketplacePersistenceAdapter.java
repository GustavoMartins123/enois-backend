package com.socialcoisa.rede.marketplace.adapters.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.socialcoisa.rede.marketplace.application.port.out.MarketplaceRepository;
import com.socialcoisa.rede.marketplace.domain.MarketplaceListing;
import com.socialcoisa.rede.marketplace.domain.MarketplacePurchase;

interface SpringDataMarketplaceListingRepository
		extends org.springframework.data.repository.Repository<MarketplaceListingJpaEntity, String> {

	MarketplaceListingJpaEntity save(MarketplaceListingJpaEntity entity);

	Optional<MarketplaceListingJpaEntity> findById(String id);

	Optional<MarketplaceListingJpaEntity> findFirstByItemKindAndItemIdAndStatus(String itemKind, String itemId,
			String status);

	List<MarketplaceListingJpaEntity> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

	List<MarketplaceListingJpaEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

	List<MarketplaceListingJpaEntity> findBySellerIdOrderByCreatedAtDesc(String sellerId);
}

interface SpringDataMarketplacePurchaseRepository
		extends org.springframework.data.repository.Repository<MarketplacePurchaseJpaEntity, String> {

	MarketplacePurchaseJpaEntity save(MarketplacePurchaseJpaEntity entity);

	Optional<MarketplacePurchaseJpaEntity> findById(String id);

	List<MarketplacePurchaseJpaEntity> findByStateAndCreatedAtBefore(String state, java.time.Instant before,
			Pageable pageable);

	List<MarketplacePurchaseJpaEntity> findByBuyerIdOrderByCreatedAtDesc(String buyerId);

	List<MarketplacePurchaseJpaEntity> findBySellerIdOrderByCreatedAtDesc(String sellerId);

	List<MarketplacePurchaseJpaEntity> findByListingIdAndState(String listingId, String state);
}

@Repository
@Transactional(propagation = Propagation.REQUIRED)
class MarketplacePersistenceAdapter implements MarketplaceRepository {

	private static final int PAGE = 200;

	private final SpringDataMarketplaceListingRepository listings;
	private final SpringDataMarketplacePurchaseRepository purchases;

	MarketplacePersistenceAdapter(SpringDataMarketplaceListingRepository listings,
			SpringDataMarketplacePurchaseRepository purchases) {
		this.listings = listings;
		this.purchases = purchases;
	}

	@Override
	public MarketplaceListing saveListing(MarketplaceListing listing) {
		return listings.save(MarketplaceListingJpaEntity.from(listing)).toDomain();
	}

	@Override
	public Optional<MarketplaceListing> findListingById(String id) {
		return listings.findById(id).map(MarketplaceListingJpaEntity::toDomain);
	}

	@Override
	public Optional<MarketplaceListing> findActiveByItem(String itemKind, String itemId) {
		return listings.findFirstByItemKindAndItemIdAndStatus(itemKind, itemId,
			MarketplaceListing.Status.ACTIVE.name()).map(MarketplaceListingJpaEntity::toDomain);
	}

	@Override
	public List<MarketplaceListing> findActive(int limit) {
		return listings.findByStatusOrderByCreatedAtDesc(MarketplaceListing.Status.ACTIVE.name(),
			Pageable.ofSize(Math.min(limit, PAGE))).stream().map(MarketplaceListingJpaEntity::toDomain).toList();
	}

	@Override
	public List<MarketplaceListing> findAll(int limit) {
		return listings.findAllByOrderByCreatedAtDesc(Pageable.ofSize(Math.min(limit, PAGE))).stream()
			.map(MarketplaceListingJpaEntity::toDomain)
			.toList();
	}

	@Override
	public List<MarketplacePurchase> findHeldByListing(String listingId) {
		return purchases.findByListingIdAndState(listingId, MarketplacePurchase.State.HELD.name()).stream()
			.map(MarketplacePurchaseJpaEntity::toDomain)
			.toList();
	}

	@Override
	public List<MarketplaceListing> findBySeller(String sellerId) {
		return listings.findBySellerIdOrderByCreatedAtDesc(sellerId).stream()
			.map(MarketplaceListingJpaEntity::toDomain).toList();
	}

	@Override
	public MarketplacePurchase savePurchase(MarketplacePurchase purchase) {
		return purchases.save(MarketplacePurchaseJpaEntity.from(purchase)).toDomain();
	}

	@Override
	public Optional<MarketplacePurchase> findPurchaseById(String id) {
		return purchases.findById(id).map(MarketplacePurchaseJpaEntity::toDomain);
	}

	@Override
	public List<MarketplacePurchase> findPurchasesByBuyer(String buyerId) {
		return purchases.findByBuyerIdOrderByCreatedAtDesc(buyerId).stream()
			.map(MarketplacePurchaseJpaEntity::toDomain).toList();
	}

	@Override
	public List<MarketplacePurchase> findSalesBySeller(String sellerId) {
		return purchases.findBySellerIdOrderByCreatedAtDesc(sellerId).stream()
			.map(MarketplacePurchaseJpaEntity::toDomain).toList();
	}

	@Override
	public List<MarketplacePurchase> findHeldOlderThan(int limit) {
		return purchases
			.findByStateAndCreatedAtBefore(MarketplacePurchase.State.HELD.name(),
				java.time.Instant.now().minus(MarketplacePurchase.DECISION_WINDOW), Pageable.ofSize(Math.min(limit, PAGE)))
			.stream().map(MarketplacePurchaseJpaEntity::toDomain).toList();
	}
}
