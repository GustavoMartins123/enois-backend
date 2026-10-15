package com.socialcoisa.rede.push.domain;

import java.time.Instant;

/**
 * Inscricao de Web Push de um dispositivo do usuario (endpoint + chaves geradas pelo navegador).
 */
public class PushSubscription {

	private final String id;
	private final String userId;
	private final String endpoint;
	private final String p256dh;
	private final String auth;
	private final Instant createdAt;
	private final Instant lastSeenAt;

	private PushSubscription(String id, String userId, String endpoint, String p256dh, String auth, Instant createdAt,
			Instant lastSeenAt) {
		this.id = id;
		this.userId = userId;
		this.endpoint = endpoint;
		this.p256dh = p256dh;
		this.auth = auth;
		this.createdAt = createdAt;
		this.lastSeenAt = lastSeenAt;
	}

	public static PushSubscription create(String id, String userId, String endpoint, String p256dh, String auth,
			Instant now) {
		return new PushSubscription(id, userId, endpoint, p256dh, auth, now, now);
	}

	public static PushSubscription restore(String id, String userId, String endpoint, String p256dh, String auth,
			Instant createdAt, Instant lastSeenAt) {
		return new PushSubscription(id, userId, endpoint, p256dh, auth, createdAt, lastSeenAt);
	}

	public String id() {
		return id;
	}

	public String userId() {
		return userId;
	}

	public String endpoint() {
		return endpoint;
	}

	public String p256dh() {
		return p256dh;
	}

	public String auth() {
		return auth;
	}

	public Instant createdAt() {
		return createdAt;
	}

	public Instant lastSeenAt() {
		return lastSeenAt;
	}
}