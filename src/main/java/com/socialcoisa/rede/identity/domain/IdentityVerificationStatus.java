package com.socialcoisa.rede.identity.domain;

/** Estados da verificacao de identidade civil. Transicoes controladas em IdentityVerification. */
public enum IdentityVerificationStatus {
	NOT_VERIFIED, PENDING, VERIFIED, REJECTED, EXPIRED, REVOKED, ERROR;

	public boolean isVerified() {
		return this == VERIFIED;
	}
}
