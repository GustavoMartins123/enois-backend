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
	/** Resposta oficial do time, enviada ao e-mail de contato (opcional). */
	private final String adminReply;
	private final String repliedBy;
	private final Instant repliedAt;
	private final Instant createdAt;
	/** Issue espelhada num repositorio privado do GitHub (null enquanto o espelho nao foi criado). */
	private final Integer githubIssueNumber;
	private final String githubIssueUrl;

	private SupportRequest(String id, String userId, String subject, String message, String contactEmail, String imageUrl,
			SupportCategory category, SupportStatus status, String adminReply, String repliedBy, Instant repliedAt,
			Instant createdAt, Integer githubIssueNumber, String githubIssueUrl) {
		this.id = id;
		this.userId = userId;
		this.subject = subject;
		this.message = message;
		this.contactEmail = contactEmail;
		this.imageUrl = imageUrl;
		this.category = category;
		this.status = status;
		this.adminReply = adminReply;
		this.repliedBy = repliedBy;
		this.repliedAt = repliedAt;
		this.createdAt = createdAt;
		this.githubIssueNumber = githubIssueNumber;
		this.githubIssueUrl = githubIssueUrl;
	}

	public static SupportRequest create(String id, String userId, String subject, String message, String contactEmail,
			String imageUrl, SupportCategory category, Instant now) {
		return new SupportRequest(id, userId, subject, message, contactEmail, imageUrl,
			category == null ? SupportCategory.OTHER : category, SupportStatus.OPEN, null, null, null, now, null,
			null);
	}

	public static SupportRequest restore(String id, String userId, String subject, String message, String contactEmail,
			String imageUrl, SupportCategory category, SupportStatus status, String adminReply, String repliedBy,
			Instant repliedAt, Instant createdAt, Integer githubIssueNumber, String githubIssueUrl) {
		return new SupportRequest(id, userId, subject, message, contactEmail, imageUrl,
			category == null ? SupportCategory.OTHER : category, status, adminReply, repliedBy, repliedAt, createdAt,
			githubIssueNumber, githubIssueUrl);
	}

	public SupportRequest withStatus(SupportStatus newStatus) {
		return new SupportRequest(id, userId, subject, message, contactEmail, imageUrl, category, newStatus,
			adminReply, repliedBy, repliedAt, createdAt, githubIssueNumber, githubIssueUrl);
	}

	public SupportRequest withCategory(SupportCategory newCategory) {
		return new SupportRequest(id, userId, subject, message, contactEmail, imageUrl, newCategory, status,
			adminReply, repliedBy, repliedAt, createdAt, githubIssueNumber, githubIssueUrl);
	}

	/** Guarda a resposta oficial; quem respondeu e quando ficam registrados para auditoria. */
	public SupportRequest withReply(String reply, String adminId, Instant at) {
		return new SupportRequest(id, userId, subject, message, contactEmail, imageUrl, category, status,
			reply, adminId, at, createdAt, githubIssueNumber, githubIssueUrl);
	}

	/** Registra a issue espelhada; nao mexe no resto do ticket. */
	public SupportRequest withGithubIssue(Integer number, String url) {
		return new SupportRequest(id, userId, subject, message, contactEmail, imageUrl, category, status,
			adminReply, repliedBy, repliedAt, createdAt, number, url);
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

	public String adminReply() {
		return adminReply;
	}

	public String repliedBy() {
		return repliedBy;
	}

	public Instant repliedAt() {
		return repliedAt;
	}

	public Instant createdAt() {
		return createdAt;
	}

	public Integer githubIssueNumber() {
		return githubIssueNumber;
	}

	public String githubIssueUrl() {
		return githubIssueUrl;
	}
}
