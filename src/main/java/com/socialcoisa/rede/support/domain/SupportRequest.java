package com.socialcoisa.rede.support.domain;

import java.time.Instant;

/**
 * Pedido de suporte aberto pelo usuario no app, para ser atendido por uma pessoa.
 */
public class SupportRequest {

	private final String id;
	private final String userId;
	private final String subject;
	private final String message;
	private final String contactEmail;
	private final SupportStatus status;
	private final Instant createdAt;

	private SupportRequest(String id, String userId, String subject, String message, String contactEmail,
			SupportStatus status, Instant createdAt) {
		this.id = id;
		this.userId = userId;
		this.subject = subject;
		this.message = message;
		this.contactEmail = contactEmail;
		this.status = status;
		this.createdAt = createdAt;
	}

	public static SupportRequest create(String id, String userId, String subject, String message, String contactEmail,
			Instant now) {
		return new SupportRequest(id, userId, subject, message, contactEmail, SupportStatus.OPEN, now);
	}

	public static SupportRequest restore(String id, String userId, String subject, String message, String contactEmail,
			SupportStatus status, Instant createdAt) {
		return new SupportRequest(id, userId, subject, message, contactEmail, status, createdAt);
	}

	public String id() {
		return id;
	}

	public String userId() {
		return userId;
	}

	public String subject() {
		return subject;
	}

	public String message() {
		return message;
	}

	public String contactEmail() {
		return contactEmail;
	}

	public SupportStatus status() {
		return status;
	}

	public Instant createdAt() {
		return createdAt;
	}
}
