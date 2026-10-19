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
	/** Anexo de imagem (URL do CDN, opcional): o print que explica o pedido. */
	private final String imageUrl;
	private final SupportCategory category;
	private final SupportStatus status;
	private final Instant createdAt;

	private SupportRequest(String id, String userId, String subject, String message, String contactEmail, String imageUrl,
			SupportCategory category, SupportStatus status, Instant createdAt) {
		this.id = id;
		this.userId = userId;
		this.subject = subject;
		this.message = message;
		this.contactEmail = contactEmail;
		this.imageUrl = imageUrl;
		this.category = category;
		this.status = status;
		this.createdAt = createdAt;
	}

	public static SupportRequest create(String id, String userId, String subject, String message, String contactEmail,
			String imageUrl, SupportCategory category, Instant now) {
		return new SupportRequest(id, userId, subject, message, contactEmail, imageUrl,
			category == null ? SupportCategory.OTHER : category, SupportStatus.OPEN, now);
	}

	public static SupportRequest restore(String id, String userId, String subject, String message, String contactEmail,
			String imageUrl, SupportCategory category, SupportStatus status, Instant createdAt) {
		return new SupportRequest(id, userId, subject, message, contactEmail, imageUrl,
			category == null ? SupportCategory.OTHER : category, status, createdAt);
	}

	public SupportRequest withStatus(SupportStatus newStatus) {
		return new SupportRequest(id, userId, subject, message, contactEmail, imageUrl, category, newStatus, createdAt);
	}

	public SupportRequest withCategory(SupportCategory newCategory) {
		return new SupportRequest(id, userId, subject, message, contactEmail, imageUrl, newCategory, status, createdAt);
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

	/** Anexo de imagem (URL do CDN, opcional). */
	public String imageUrl() {
		return imageUrl;
	}

	public SupportCategory category() {
		return category;
	}

	public SupportStatus status() {
		return status;
	}

	public Instant createdAt() {
		return createdAt;
	}
}
