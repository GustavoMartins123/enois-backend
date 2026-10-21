package com.socialcoisa.rede.identity.adapters.out.persistence;

import java.time.Instant;
import java.time.LocalDate;

import com.socialcoisa.rede.identity.domain.PendingSignup;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "pending_signups")
class PendingSignupJpaEntity {

	@Id
	@Column(name = "id", length = 64, nullable = false)
	private String id;

	@Column(name = "email", length = 255, nullable = false)
	private String email;

	@Column(name = "name", length = 120, nullable = false)
	private String name;

	@Column(name = "birth_date", nullable = false)
	private LocalDate birthDate;

	@Column(name = "password_hash", length = 255, nullable = false)
	private String passwordHash;

	@Column(name = "token_hash", length = 64, nullable = false)
	private String tokenHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected PendingSignupJpaEntity() {
	}

	static PendingSignupJpaEntity fromDomain(PendingSignup domain) {
		PendingSignupJpaEntity entity = new PendingSignupJpaEntity();
		entity.id = domain.id();
		entity.email = domain.email();
		entity.name = domain.name();
		entity.birthDate = domain.birthDate();
		entity.passwordHash = domain.passwordHash();
		entity.tokenHash = domain.tokenHash();
		entity.expiresAt = domain.expiresAt();
		entity.createdAt = domain.createdAt();
		return entity;
	}

	PendingSignup toDomain() {
		return new PendingSignup(id, email, name, birthDate, passwordHash, tokenHash, expiresAt, createdAt);
	}
}
