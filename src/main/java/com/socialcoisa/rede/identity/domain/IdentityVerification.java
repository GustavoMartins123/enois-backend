package com.socialcoisa.rede.identity.domain;

import java.time.Instant;
import java.util.Set;

import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Verificacao de identidade de um usuario. Estados explicitos e transicoes controladas — o front
 * nunca define VERIFIED; so o processamento de um retorno legitimo do provedor chega la.
 */
public class IdentityVerification {

	/** Transicoes permitidas a partir de cada estado. */
	private static final Set<String> ALLOWED = Set.of(
		"NOT_VERIFIED>PENDING", "PENDING>VERIFIED", "PENDING>REJECTED", "PENDING>EXPIRED", "PENDING>ERROR",
		"PENDING>REVOKED", "VERIFIED>EXPIRED", "VERIFIED>REVOKED", "REJECTED>PENDING", "EXPIRED>PENDING",
		"ERROR>PENDING", "REVOKED>PENDING");

	private final String id;
	private final String userId;
	private final String provider;
	private final IdentityVerificationStatus state;
	private final String attemptId;
	private final String reasonCode;
	private final Instant startedAt;
	private final Instant completedAt;
	private final Instant expiresAt;
	private final Instant lastValidatedAt;
	private final String consentVersion;
	private final Instant updatedAt;

	public IdentityVerification(String id, String userId, String provider, IdentityVerificationStatus state,
			String attemptId, String reasonCode, Instant startedAt, Instant completedAt, Instant expiresAt,
			Instant lastValidatedAt, String consentVersion, Instant updatedAt) {
		this.id = id;
		this.userId = userId;
		this.provider = provider;
		this.state = state;
		this.attemptId = attemptId;
		this.reasonCode = reasonCode;
		this.startedAt = startedAt;
		this.completedAt = completedAt;
		this.expiresAt = expiresAt;
		this.lastValidatedAt = lastValidatedAt;
		this.consentVersion = consentVersion;
		this.updatedAt = updatedAt;
	}

	public static IdentityVerification notVerified(String id, String userId, Instant now) {
		return new IdentityVerification(id, userId, null, IdentityVerificationStatus.NOT_VERIFIED, null, null,
			null, null, null, null, null, now);
	}

	public IdentityVerification transitionTo(IdentityVerificationStatus next, String provider, String attemptId,
			Instant now) {
		if (!ALLOWED.contains(state.name() + ">" + next.name())) {
			throw new ApiException(409, "INVALID_IDENTITY_TRANSITION",
				"Transicao de estado de verificacao invalida.");
		}
		Instant completed = (next == IdentityVerificationStatus.VERIFIED
				|| next == IdentityVerificationStatus.REJECTED) ? now : completedAt;
		Instant validated = next == IdentityVerificationStatus.VERIFIED ? now : lastValidatedAt;
		Instant started = next == IdentityVerificationStatus.PENDING ? now : startedAt;
		return new IdentityVerification(id, userId,
			provider != null ? provider : this.provider, next,
			attemptId != null ? attemptId : this.attemptId, reasonCode, started, completed, expiresAt, validated,
			consentVersion, now);
	}

	public IdentityVerification withReason(String reasonCode, Instant now) {
		return new IdentityVerification(id, userId, provider, state, attemptId, reasonCode, startedAt, completedAt,
			expiresAt, lastValidatedAt, consentVersion, now);
	}

	/** Continuidade da vinculacao: expirada se a janela venceu. */
	public boolean isValidAt(Instant now) {
		return state == IdentityVerificationStatus.VERIFIED
			&& (expiresAt == null || expiresAt.isAfter(now));
	}

	public String id() { return id; }
	public String userId() { return userId; }
	public String provider() { return provider; }
	public IdentityVerificationStatus state() { return state; }
	public String attemptId() { return attemptId; }
	public String reasonCode() { return reasonCode; }
	public Instant startedAt() { return startedAt; }
	public Instant completedAt() { return completedAt; }
	public Instant expiresAt() { return expiresAt; }
	public Instant lastValidatedAt() { return lastValidatedAt; }
	public String consentVersion() { return consentVersion; }
	public Instant updatedAt() { return updatedAt; }
}
