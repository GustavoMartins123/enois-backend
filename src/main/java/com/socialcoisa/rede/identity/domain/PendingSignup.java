package com.socialcoisa.rede.identity.domain;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Um cadastro aguardando a confirmacao do e-mail — a conta ainda NAO existe.
 *
 * A senha fica aqui apenas como hash (o mesmo que a conta vai receber depois); o token cru existe
 * somente no link enviado por e-mail. Quando o link e usado, o cadastro vira conta e a linha some.
 */
public record PendingSignup(String id, String email, String name, LocalDate birthDate, String passwordHash,
		String tokenHash, Instant expiresAt, Instant createdAt, int resendCount, Instant lastSentAt) {

	public boolean isExpired(Instant now) {
		return !expiresAt.isAfter(now);
	}

	/** Reemissao do link: troca o token e a validade, mantendo os dados do cadastro. */
	public PendingSignup reissued(String newTokenHash, Instant newExpiresAt) {
		return new PendingSignup(id, email, name, birthDate, passwordHash, newTokenHash, newExpiresAt, createdAt,
			resendCount, lastSentAt);
	}

	/** Novo envio do link: conta o envio e anota quando foi (para o limite por janela). */
	public PendingSignup resent(String newTokenHash, Instant newExpiresAt, Instant sentAt) {
		return new PendingSignup(id, email, name, birthDate, passwordHash, newTokenHash, newExpiresAt, createdAt,
			resendCount + 1, sentAt);
	}

	/**
	 * Quantos envios de link aconteceram dentro da janela. A janela reinicia depois de uma hora
	 * sem envio — o mesmo criterio do fluxo de conta ja criada.
	 */
	public int sendsWithin(Instant now, java.time.Duration window) {
		if (lastSentAt == null || !lastSentAt.isAfter(now.minus(window))) {
			return 0;
		}
		return resendCount;
	}
}
