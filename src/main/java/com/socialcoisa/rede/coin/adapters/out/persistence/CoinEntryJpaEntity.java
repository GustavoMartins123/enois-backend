package com.socialcoisa.rede.coin.adapters.out.persistence;

import java.time.Instant;

import com.socialcoisa.rede.coin.domain.CoinEntry;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "coin_entries", indexes = @Index(name = "idx_coin_entries_account", columnList = "account_id, created_at"))
class CoinEntryJpaEntity {

	@Id
	private String id;

	@Column(name = "account_id", nullable = false)
	private String accountId;

	@Column(nullable = false)
	private long amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 24)
	private CoinEntry.Kind kind;

	@Column(name = "ref_id", nullable = false)
	private String refId;

	@Column(length = 300)
	private String message;

	@Column(name = "idempotency_key", nullable = false)
	private String idempotencyKey;

	@Column(name = "prev_hash", nullable = false, length = 64)
	private String prevHash;

	@Column(nullable = false, length = 64)
	private String hash;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected CoinEntryJpaEntity() {
	}

	static CoinEntryJpaEntity fromDomain(CoinEntry entry) {
		CoinEntryJpaEntity e = new CoinEntryJpaEntity();
		e.id = entry.id();
		e.accountId = entry.accountId();
		e.amount = entry.amount();
		e.kind = entry.kind();
		e.refId = entry.refId();
		e.message = entry.message();
		e.idempotencyKey = entry.idempotencyKey();
		e.prevHash = entry.prevHash();
		e.hash = entry.hash();
		e.createdAt = entry.createdAt();
		return e;
	}

	CoinEntry toDomain() {
		return CoinEntry.create(id, accountId, amount, kind, refId, message, idempotencyKey, prevHash, hash,
			createdAt);
	}
}
