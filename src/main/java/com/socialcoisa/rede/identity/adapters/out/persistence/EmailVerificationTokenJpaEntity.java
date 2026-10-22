package com.socialcoisa.rede.identity.adapters.out.persistence;

import java.time.Instant;

import com.socialcoisa.rede.identity.domain.EmailVerificationToken;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "email_verification_tokens", indexes = {
		@Index(name = "idx_email_verification_tokens_user", columnList = "user_id"),
		@Index(name = "idx_email_verification_tokens_expires", columnList = "expires_at") })
class EmailVerificationTokenJpaEntity {

	@Id
	private String token;

	@Column(name = "user_id", nullable = false)
	private String userId;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "used_at")
	private Instant usedAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected EmailVerificationTokenJpaEntity() {
	}

	static EmailVerificationTokenJpaEntity fromDomain(EmailVerificationToken domain) {
		EmailVerificationTokenJpaEntity entity = new EmailVerificationTokenJpaEntity();
		entity.token = domain.token();
		entity.userId = domain.userId();
		entity.expiresAt = domain.expiresAt();
		entity.usedAt = domain.usedAt();
		entity.createdAt = domain.createdAt();
		return entity;
	}

	EmailVerificationToken toDomain() {
		return EmailVerificationToken.restore(token, userId, expiresAt, usedAt, createdAt);
	}

	void markUsed(Instant now) {
		this.usedAt = now;
	}
}