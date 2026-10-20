package com.socialcoisa.rede.moderation.adapters.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "blocked_ips")
class BlockedIpJpaEntity {

	@Id
	private String id;

	@Column(nullable = false, length = 64)
	private String ip;

	@Column(length = 2000)
	private String reason;

	@Column(name = "created_by")
	private String createdBy;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected BlockedIpJpaEntity() {
	}

	BlockedIpJpaEntity(String id, String ip, String reason, String createdBy, Instant createdAt) {
		this.id = id;
		this.ip = ip;
		this.reason = reason;
		this.createdBy = createdBy;
		this.createdAt = createdAt;
	}

	com.socialcoisa.rede.moderation.domain.BlockedIp toDomain() {
		return new com.socialcoisa.rede.moderation.domain.BlockedIp(id, ip, reason, createdBy, createdAt);
	}
}
