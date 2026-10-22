package com.socialcoisa.rede.coin.domain;

import java.time.Instant;

/** Snapshot do saldo derivado do ledger, com versão para atualização otimista. */
public record CoinAccount(String accountId, long balance, long version, Instant createdAt) {

	public static CoinAccount create(String accountId, long initialBalance, Instant now) {
		return new CoinAccount(accountId, initialBalance, 0, now);
	}

	public CoinAccount withBalance(long newBalance, long nextVersion) {
		return new CoinAccount(accountId, newBalance, nextVersion, createdAt);
	}
}
