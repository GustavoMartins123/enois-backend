package com.socialcoisa.rede.identity.domain;

import java.time.Instant;

/**
 * Um pedido de autorizacao OAuth iniciado por nos.
 *
 * Existe para o callback poder conferir tres coisas que um cookie sozinho nao garante: que o
 * `state` foi emitido por esta aplicacao, que ele ainda vale e que e de uso unico. O `nonce` viaja
 * ate o provedor e volta dentro do id_token, amarrando o token a este pedido.
 *
 * Quando {@code userId} esta preenchido, o fluxo e de vinculacao: o provedor sera ligado a essa
 * conta em vez de entrar/criar outra.
 */
public class OAuthAuthorizationRequest {

	private final String state;
	private final OAuthProvider provider;
	private final String nonce;
	private final String userId;
	private final Instant expiresAt;
	private final Instant createdAt;

	private OAuthAuthorizationRequest(String state, OAuthProvider provider, String nonce, String userId,
			Instant expiresAt, Instant createdAt) {
		this.state = state;
		this.provider = provider;
		this.nonce = nonce;
		this.userId = userId;
		this.expiresAt = expiresAt;
		this.createdAt = createdAt;
	}

	public static OAuthAuthorizationRequest create(String state, OAuthProvider provider, String nonce, String userId,
			Instant expiresAt, Instant now) {
		return new OAuthAuthorizationRequest(state, provider, nonce, userId, expiresAt, now);
	}

	public static OAuthAuthorizationRequest restore(String state, OAuthProvider provider, String nonce, String userId,
			Instant expiresAt, Instant createdAt) {
		return new OAuthAuthorizationRequest(state, provider, nonce, userId, expiresAt, createdAt);
	}

	public boolean isExpired(Instant now) {
		return now.isAfter(expiresAt);
	}

	public boolean isLinkRequest() {
		return userId != null && !userId.isBlank();
	}

	public String state() {
		return state;
	}

	public OAuthProvider provider() {
		return provider;
	}

	public String nonce() {
		return nonce;
	}

	public String userId() {
		return userId;
	}

	public Instant expiresAt() {
		return expiresAt;
	}

	public Instant createdAt() {
		return createdAt;
	}
}