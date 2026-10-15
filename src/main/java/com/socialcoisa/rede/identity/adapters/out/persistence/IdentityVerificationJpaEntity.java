package com.socialcoisa.rede.identity.adapters.out.persistence;

import java.time.Instant;

import com.socialcoisa.rede.identity.domain.IdentityVerification;
import com.socialcoisa.rede.identity.domain.IdentityVerificationStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "identity_verifications", indexes = @Index(name = "idx_identity_verifications_state", columnList = "state"))
class IdentityVerificationJpaEntity {

	@Id
	private String id;

	@Column(name = "user_id", nullable = false, unique = true)
	private String userId;

	private String provider;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private IdentityVerificationStatus state;

	@Column(name = "attempt_id")
	private String attemptId;

	@Column(name = "reason_code")
	private String reasonCode;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "completed_at")
	private Instant completedAt;

	@Column(name = "expires_at")
	private Instant expiresAt;

	@Column(name = "last_validated_at")
	private Instant lastValidatedAt;

	@Column(name = "consent_version")
	private String consentVersion;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected IdentityVerificationJpaEntity() {
	}

	static IdentityVerificationJpaEntity fromDomain(IdentityVerification v) {
		IdentityVerificationJpaEntity e = new IdentityVerificationJpaEntity();
		e.id = v.id();
		e.userId = v.userId();
		e.provider = v.provider();
		e.state = v.state();
		e.attemptId = v.attemptId();
		e.reasonCode = v.reasonCode();
		e.startedAt = v.startedAt();
		e.completedAt = v.completedAt();
		e.expiresAt = v.expiresAt();
		e.lastValidatedAt = v.lastValidatedAt();
		e.consentVersion = v.consentVersion();
		e.updatedAt = v.updatedAt();
		return e;
	}

	IdentityVerification toDomain() {
		return new IdentityVerification(id, userId, provider, state, attemptId, reasonCode, startedAt, completedAt,
			expiresAt, lastValidatedAt, consentVersion, updatedAt);
	}
}
