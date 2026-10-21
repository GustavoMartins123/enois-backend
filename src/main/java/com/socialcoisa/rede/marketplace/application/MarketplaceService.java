package com.socialcoisa.rede.marketplace.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.socialcoisa.rede.coin.application.CoinService;
import com.socialcoisa.rede.marketplace.application.port.out.MarketplaceRepository;
import com.socialcoisa.rede.marketplace.domain.MarketplaceListing;
import com.socialcoisa.rede.marketplace.domain.MarketplacePurchase;
import com.socialcoisa.rede.shared.web.ApiException;

/**
 * O marketplace de itens (Fase 4): v1 vende TEMA da galeria de temas. Tudo passa por
 * ESCROW — as faíscas saem do comprador e ficam retidas até a decisão (7 dias):
 * confirmar libera para o criador (−10% de taxa); reembolso devolve 100%. O pacote que
 * quebra a validação do lado do comprador é reembolso, não prejuízo.
 */
@Service
public class MarketplaceService {

	/** Piso de preço do marketplace: 50 faíscas (5% de uma "moeda" na escala interna). */
	public static final long MIN_PRICE = CoinService.FAIS(50L);

	private final MarketplaceRepository listings;
	private final CoinService coins;
	private final Clock clock;

	public MarketplaceService(MarketplaceRepository listings, CoinService coins, Clock clock) {
		this.listings = listings;
		this.coins = coins;
		this.clock = clock;
	}

	@Transactional
	public MarketplaceListing publish(String sellerId, String itemKind, String itemId, String title,
			String description, long price) {
		MarketplaceListing.ItemKind kind = parseKind(itemKind);
		if (price < MIN_PRICE) {
			throw new ApiException(400, "PRICE_TOO_LOW", "O preço mínimo no marketplace é de 50 faíscas.");
		}
		Instant now = clock.instant();
		listings.findActiveByItem(kind.name(), itemId).ifPresent(existing -> {
			throw new ApiException(409, "ITEM_ALREADY_LISTED", "Este item já está à venda.");
		});
		return listings.saveListing(MarketplaceListing.create(sellerId, kind, itemId, title, description, price, now));
	}

	/**
	 * Garante que um item da galeria apareça no marketplace com o preço mínimo. Idempotente:
	 * se já existe listagem ativa, não faz nada. Usado na migração dos temas da comunidade.
	 */
	@Transactional
	public java.util.Optional<MarketplaceListing> ensureListed(String sellerId, String itemKind, String itemId,
			String title, String description, long price) {
		MarketplaceListing.ItemKind kind = parseKind(itemKind);
		java.util.Optional<MarketplaceListing> existing = listings.findActiveByItem(kind.name(), itemId);
		if (existing.isPresent()) {
			return java.util.Optional.empty();
		}
		return java.util.Optional.of(publish(sellerId, itemKind, itemId, title, description, price));
	}

	@Transactional
	public MarketplacePurchase buy(String listingId, String buyerId) {
		MarketplaceListing listing = listing(listingId);
		if (listing.status() != MarketplaceListing.Status.ACTIVE) {
			throw new ApiException(409, "LISTING_NOT_ACTIVE", "Esta listagem não está mais aberta.");
		}
		if (listing.ownedBy(buyerId)) {
			throw new ApiException(400, "OWN_ITEM", "Não dá para comprar o seu próprio item.");
		}
		Instant now = clock.instant();
		String purchaseRef = "mkt_" + listing.id().substring(4) + ":" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
		coins.escrowHold(buyerId, listing.price(), purchaseRef,
			"Compra no marketplace: " + listing.title());
		MarketplacePurchase purchase = listings
			.savePurchase(MarketplacePurchase.create(listing.id(), buyerId, listing.sellerId(), listing.price(), now));
		listings.saveListing(listing.markSold(now));
		return purchase;
	}

	/** O comprador confirma (instalou e está satisfeito): criador recebe − 10%. */
	@Transactional
	public MarketplacePurchase confirm(String purchaseId, String buyerId) {
		MarketplacePurchase purchase = purchase(purchaseId);
		if (!purchase.ownedBy(buyerId)) {
			throw new ApiException(403, "NOT_THE_BUYER", "Só quem comprou confirma a compra.");
		}
		Instant now = clock.instant();
		MarketplacePurchase released = purchase.release(now);
		coins.escrowRelease(purchase.sellerId(), purchase.price(), escrowRef(purchase),
			"Venda no marketplace (escrow liberado)");
		return listings.savePurchase(released);
	}

	/** O comprador pede reembolso dentro da janela: devolve 100%. */
	@Transactional
	public MarketplacePurchase refund(String purchaseId, String buyerId) {
		MarketplacePurchase purchase = purchase(purchaseId);
		if (!purchase.ownedBy(buyerId)) {
			throw new ApiException(403, "NOT_THE_BUYER", "Só quem comprou pede o reembolso.");
		}
		Instant now = clock.instant();
		MarketplacePurchase refunded = purchase.refund(now);
		coins.escrowRefund(purchase.buyerId(), purchase.price(), escrowRef(purchase),
			"Reembolso de compra no marketplace");
		return listings.savePurchase(refunded);
	}

	/**
	 * Auto-liberação: compras HELD sem decisão passada a janela liberam para o vendedor
	 * (o criador não fica esperando para sempre). Roda por hora.
	 */
	@Scheduled(fixedDelay = 3_600_000, initialDelay = 600_000)
	@Transactional
	public void releaseExpired() {
		Instant now = clock.instant();
		for (MarketplacePurchase purchase : listings.findHeldOlderThan(50)) {
			if (purchase.decisionExpired(now)) {
				try {
					coins.escrowRelease(purchase.sellerId(), purchase.price(), escrowRef(purchase),
						"Venda no marketplace (liberação automática)");
					listings.savePurchase(purchase.release(now));
				} catch (RuntimeException ignored) {
					// a próxima rodada tenta de novo; nada aqui pode derrubar o worker
				}
			}
		}
	}

	public List<MarketplaceListing> catalog(int limit) {
		return listings.findActive(limit);
	}

	public List<MarketplaceListing> mine(String sellerId) {
		return listings.findBySeller(sellerId);
	}

	/** Compras de quem comprou (o painel do escrow: confirmar ou pedir reembolso). */
	public List<MarketplacePurchase> purchasesByBuyer(String buyerId) {
		return listings.findPurchasesByBuyer(buyerId);
	}

	/** Vendas de quem vendeu (o painel do escrow: acompanhar as liberações). */
	public List<MarketplacePurchase> salesBySeller(String sellerId) {
		return listings.findSalesBySeller(sellerId);
	}

	private MarketplaceListing listing(String id) {
		return listings.findListingById(id)
			.orElseThrow(() -> new ApiException(404, "LISTING_NOT_FOUND", "Listagem não encontrada."));
	}

	private MarketplacePurchase purchase(String id) {
		return listings.findPurchaseById(id)
			.orElseThrow(() -> new ApiException(404, "PURCHASE_NOT_FOUND", "Compra não encontrada."));
	}

	private MarketplaceListing.ItemKind parseKind(String raw) {
		if (!StringUtils.hasText(raw)) {
			return MarketplaceListing.ItemKind.THEME;
		}
		try {
			return MarketplaceListing.ItemKind.valueOf(raw.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new ApiException(400, "INVALID_ITEM_KIND", "Tipo de item inválido.");
		}
	}

	private String escrowRef(MarketplacePurchase purchase) {
		return "mcmp:" + purchase.id();
	}
}
