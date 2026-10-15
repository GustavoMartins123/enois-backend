package com.socialcoisa.rede.identity.domain;

import java.time.Instant;

/**
 * Token de confirmacao do e-mail informado no cadastro. Guarda apenas o hash (o valor em claro so
 * existe no link enviado por e-mail), com validade e uso unico.
 */
public class EmailVerificationToken {

	private final String token;
	private final String userId;
	private final Instant expiresAt;
	private final Instant usedAt;
	private final Instant createdAt;

	private EmailVerificationToken(String token, String userId, Instant expiresAt, Instant usedAt, Instant createdAt) {
		this.token = token;
		this.userId = userId;
		this.expiresAt = expiresAt;
		this.usedAt = usedAt;
		this.createdAt = createdAt;
	}

	public static EmailVerificationToken create(String token, String userId, Instant expiresAt, Instant now) {
		return new EmailVerificationToken(token, userId, expiresAt, null, now);
	}

	public static EmailVerificationToken restore(String token, String userId, Instant expiresAt, Instant usedAt,
			Instant createdAt) {
		return new EmailVerificationToken(token, userId, expiresAt, usedAt, createdAt);
	}

	public boolean isExpired(Instant now) {
		return now.isAfter(expiresAt);
	}

	public boolean isUsed() {
		return usedAt != null;
	}

	public String token() {
		return token;
	}

	public String userId() {
		return userId;
	}

	public Instant expiresAt() {
		return expiresAt;
	}

	public Instant usedAt() {
		return usedAt;
	}

	public Instant createdAt() {
		return createdAt;
	}
}