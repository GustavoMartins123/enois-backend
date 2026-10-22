package com.socialcoisa.rede.coin.domain;

import java.time.Instant;

/** Lançamento do ledger: append-only, com hash encadeado ao anterior da MESMA conta. */
public record CoinEntry(String id, String accountId, long amount, Kind kind, String refId, String message,
		String idempotencyKey, String prevHash, String hash, Instant createdAt) {

	public enum Kind {
		AIRDROP, GENESIS_TREASURY, TRANSFER_OUT, TRANSFER_IN, REWARD, PURCHASE, SALE, REFUND, ADJUSTMENT, ESCROW_OUT, ESCROW_HOLD, ESCROW_RELEASE, ESCROW_IN, REFUND_IN, FEE_IN, REVERSAL_OUT, REVERSAL_IN
	}

	public static CoinEntry create(String id, String accountId, long amount, Kind kind, String refId, String message,
			String idempotencyKey, String prevHash, String hash, Instant createdAt) {
		return new CoinEntry(id, accountId, amount, kind, refId, message, idempotencyKey, prevHash, hash, createdAt);
	}

	/** Cópia com a mensagem decifrada (o hash NÃO muda: o ledger guarda a mensagem cifrada). */
	public CoinEntry withMessage(String novaMensagem) {
		return new CoinEntry(id, accountId, amount, kind, refId, novaMensagem, idempotencyKey, prevHash, hash,
			createdAt);
	}
}
