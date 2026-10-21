package com.socialcoisa.rede.age.domain;

import java.time.Instant;

/**
 * Convite/vinculo entre um menor e seu responsavel legal.
 *
 * O responsavel e convidado por e-mail e precisa ter conta propria de adulto; o token (uso unico,
 * com validade) e o unico caminho para aceitar. A autorizacao fica registrada com a versao dos
 * termos aceitos e a data da decisao — nao existe "checkbox" declaratorio pelo menor.
 */
public record ParentalLink(String id, String minorUserId, String guardianUserId, String guardianEmail,
		Status status, String tokenHash, String termsVersion, Instant createdAt, Instant expiresAt,
		Instant decidedAt, Instant revokedAt) {

	public enum Status {
		PENDING,
		ACTIVE,
		REJECTED,
		REVOKED
	}

	public boolean isPending() {
		return status == Status.PENDING;
	}

	public boolean isActive() {
		return status == Status.ACTIVE;
	}

	public boolean isExpired(Instant now) {
		return status == Status.PENDING && !expiresAt.isAfter(now);
	}

	public ParentalLink acceptedBy(String guardianId, Instant at) {
		return new ParentalLink(id, minorUserId, guardianId, guardianEmail, Status.ACTIVE, tokenHash, termsVersion,
			createdAt, expiresAt, at, null);
	}

	public ParentalLink rejectedBy(String guardianId, Instant at) {
		return new ParentalLink(id, minorUserId, guardianId, guardianEmail, Status.REJECTED, tokenHash, termsVersion,
			createdAt, expiresAt, at, null);
	}

	public ParentalLink revoked(Instant at) {
		return new ParentalLink(id, minorUserId, guardianUserId, guardianEmail, Status.REVOKED, tokenHash,
			termsVersion, createdAt, expiresAt, decidedAt, at);
	}
}
