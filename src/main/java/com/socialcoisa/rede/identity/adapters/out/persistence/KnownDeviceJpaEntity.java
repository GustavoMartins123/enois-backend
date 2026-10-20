package com.socialcoisa.rede.identity.adapters.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "known_devices")
class KnownDeviceJpaEntity {

	@Id
	@Column(length = 64)
	private String id;

	@Column(name = "user_id", nullable = false, length = 64)
	private String userId;

	@Column(nullable = false, length = 128)
	private String fingerprint;

	@Column(name = "first_seen_at", nullable = false)
	private Instant firstSeenAt;

	protected KnownDeviceJpaEntity() {
	}

	KnownDeviceJpaEntity(String id, String userId, String fingerprint, Instant firstSeenAt) {
		this.id = id;
		this.userId = userId;
		this.fingerprint = fingerprint;
		this.firstSeenAt = firstSeenAt;
	}

	String userId() { return userId; }
	String fingerprint() { return fingerprint; }
}
