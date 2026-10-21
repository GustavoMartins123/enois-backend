package com.socialcoisa.rede.age.adapters.out.persistence;

import java.time.Instant;

import com.socialcoisa.rede.age.domain.ParentalLink;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "parental_links")
class ParentalLinkJpaEntity {

	@Id
	@Column(name = "id", length = 64, nullable = false)
	private String id;

	@Column(name = "minor_user_id", length = 64, nullable = false)
	private String minorUserId;

	@Column(name = "guardian_user_id", length = 64)
	private String guardianUserId;

	@Column(name = "guardian_email", length = 255, nullable = false)
	private String guardianEmail;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", length = 20, nullable = false)
	private ParentalLink.Status status;

	@Column(name = "token_hash", length = 64)
	private String tokenHash;

	@Column(name = "terms_version", length = 20, nullable = false)
	private String termsVersion;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "decided_at")
	private Instant decidedAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	protected ParentalLinkJpaEntity() {
	}

	static ParentalLinkJpaEntity fromDomain(ParentalLink domain) {
		ParentalLinkJpaEntity entity = new ParentalLinkJpaEntity();
		entity.id = domain.id();
		entity.minorUserId = domain.minorUserId();
		entity.guardianUserId = domain.guardianUserId();
		entity.guardianEmail = domain.guardianEmail();
		entity.status = domain.status();
		entity.tokenHash = domain.tokenHash();
		entity.termsVersion = domain.termsVersion();
		entity.createdAt = domain.createdAt();
		entity.expiresAt = domain.expiresAt();
		entity.decidedAt = domain.decidedAt();
		entity.revokedAt = domain.revokedAt();
		return entity;
	}

	ParentalLink toDomain() {
		return new ParentalLink(id, minorUserId, guardianUserId, guardianEmail, status, tokenHash, termsVersion,
			createdAt, expiresAt, decidedAt, revokedAt);
	}
}
