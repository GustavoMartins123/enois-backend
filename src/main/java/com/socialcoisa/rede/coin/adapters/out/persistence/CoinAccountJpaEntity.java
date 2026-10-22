package com.socialcoisa.rede.coin.adapters.out.persistence;

import java.time.Instant;

import com.socialcoisa.rede.coin.domain.CoinAccount;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "coin_accounts")
class CoinAccountJpaEntity {

	@Id
	@Column(name = "account_id")
	private String accountId;

	@Column(nullable = false)
	private long balance;

	@Version
	@Column(nullable = false)
	private long version;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected CoinAccountJpaEntity() {
	}

	static CoinAccountJpaEntity fromDomain(CoinAccount account) {
		CoinAccountJpaEntity e = new CoinAccountJpaEntity();
		e.accountId = account.accountId();
		e.balance = account.balance();
		e.version = account.version();
		e.createdAt = account.createdAt();
		return e;
	}

	CoinAccount toDomain() {
		return new CoinAccount(accountId, balance, version, createdAt);
	}
}
