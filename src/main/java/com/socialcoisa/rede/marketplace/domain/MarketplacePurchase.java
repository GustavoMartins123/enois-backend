package com.socialcoisa.rede.marketplace.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.socialcoisa.rede.shared.web.ApiException;

/**
 * A compra com escrow: as faíscas ficam RETIDAS (HELD) até o comprador confirmar —
 * aí o criador recebe preço − 10% — ou o comprador pedir reembolso (devolve 100%).
 * Depois da janela, a liberação é automática: quem comprou não esquece o vendedor esperando.
 */
public record MarketplacePurchase(String id, String listingId, String buyerId, String sellerId, long price,
		State state, Instant createdAt, Instant decidedAt) {

	public enum State {
		HELD, RELEASED, REFUNDED
	}

	/** Janela do comprador: 7 dias para confirmar ou pedir reembolso. */
	public static final Duration DECISION_WINDOW = Duration.ofDays(7);

	public static MarketplacePurchase create(String listingId, String buyerId, String sellerId, long price,
			Instant now) {
		return new MarketplacePurchase("mcmp_" + UUID.randomUUID().toString().replace("-", ""), listingId, buyerId,
			sellerId, price, State.HELD, now, null);
	}

	public boolean ownedBy(String accountId) {
		return buyerId != null && buyerId.equals(accountId);
	}

	public MarketplacePurchase release(Instant now) {
		if (state != State.HELD) {
			throw new ApiException(409, "PURCHASE_NOT_HELD", "Esta compra já foi decidida.");
		}
		return new MarketplacePurchase(id, listingId, buyerId, sellerId, price, State.RELEASED, createdAt, now);
	}

	public MarketplacePurchase refund(Instant now) {
		if (state != State.HELD) {
			throw new ApiException(409, "PURCHASE_NOT_HELD", "Esta compra já foi decidida.");
		}
		if (Duration.between(createdAt, now).compareTo(DECISION_WINDOW) > 0) {
			throw new ApiException(403, "REFUND_WINDOW_CLOSED",
				"O prazo de reembolso (7 dias) já fechou — a liberação é automática.");
		}
		return new MarketplacePurchase(id, listingId, buyerId, sellerId, price, State.REFUNDED, createdAt, now);
	}

	public boolean decisionExpired(Instant now) {
		return state == State.HELD && Duration.between(createdAt, now).compareTo(DECISION_WINDOW) > 0;
	}
}
