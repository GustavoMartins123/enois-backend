package com.socialcoisa.rede.identity.adapters.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "auth_sessions", indexes = @Index(name = "idx_auth_sessions_user_expires", columnList = "user_id, expires_at"))
class AuthSessionJpaEntity {

	@Id
	private String token;

	@Column(name = "refresh_token", unique = true)
	private String refreshToken;

	@Column(name = "user_id", nullable = false)
	private String userId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	/** Validade do refresh token (30 dias), independente do access token. */
	@Column(name = "refresh_expires_at")
	private Instant refreshExpiresAt;

	/** IP de onde a sessao nasceu (V115); nulo para sessoes antigas e fluxos sem o cabecalho. */
	@Column(name = "ip")
	private String ip;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	protected AuthSessionJpaEntity() {
	}

	AuthSessionJpaEntity(String token, String refreshToken, String userId, Instant createdAt, Instant expiresAt,
			Instant refreshExpiresAt) {
		this(token, refreshToken, userId, createdAt, expiresAt, refreshExpiresAt, null);
	}

	AuthSessionJpaEntity(String token, String refreshToken, String userId, Instant createdAt, Instant expiresAt,
			Instant refreshExpiresAt, String ip) {
		this.token = token;
		this.refreshToken = refreshToken;
		this.userId = userId;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
		this.refreshExpiresAt = refreshExpiresAt;
		this.ip = ip;
	}

	String token() {
		return token;
	}

	String userId() {
		return userId;
	}

	String refreshToken() {
		return refreshToken;
	}

	boolean activeAt(Instant now) {
		return revokedAt == null && expiresAt.isAfter(now);
	}

	/** Validade do refresh: usa a janela propria (30 dias); sessoes antigas caem no fallback. */
	boolean activeRefreshAt(Instant now) {
		Instant limit = refreshExpiresAt != null ? refreshExpiresAt : expiresAt;
		return revokedAt == null && limit.isAfter(now);
	}

	void revoke(Instant revokedAt) {
		this.revokedAt = revokedAt;
	}
}
