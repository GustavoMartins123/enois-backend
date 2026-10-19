package com.socialcoisa.rede.support.adapters.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import com.socialcoisa.rede.support.domain.SupportCategory;
import com.socialcoisa.rede.support.domain.SupportRequest;
import com.socialcoisa.rede.support.domain.SupportStatus;

@Entity
@Table(name = "support_requests", indexes = { @Index(name = "idx_support_requests_created", columnList = "created_at"),
		@Index(name = "idx_support_requests_status", columnList = "status") })
class SupportRequestJpaEntity {

	@Id
	private String id;

	@Column(name = "user_id", nullable = false)
	private String userId;

	@Column(nullable = false, length = 160)
	private String subject;

	@Column(nullable = false, length = 4000)
	private String message;

	@Column(name = "contact_email", nullable = false)
	private String contactEmail;

	/** Anexo de imagem (URL do CDN, opcional): o print que explica o pedido. */
	@Column(name = "image_url", length = 500)
	private String imageUrl;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 24)
	private SupportCategory category;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private SupportStatus status;

	@Column(name = "admin_reply", length = 4000)
	private String adminReply;

	@Column(name = "replied_by")
	private String repliedBy;

	@Column(name = "replied_at")
	private Instant repliedAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected SupportRequestJpaEntity() {
	}

	static SupportRequestJpaEntity fromDomain(SupportRequest request) {
		SupportRequestJpaEntity entity = new SupportRequestJpaEntity();
		entity.id = request.id();
		entity.userId = request.userId();
		entity.subject = request.subject();
		entity.message = request.message();
		entity.contactEmail = request.contactEmail();
		entity.imageUrl = request.imageUrl();
		entity.category = request.category();
		entity.status = request.status();
		entity.adminReply = request.adminReply();
		entity.repliedBy = request.repliedBy();
		entity.repliedAt = request.repliedAt();
		entity.createdAt = request.createdAt();
		return entity;
	}

	SupportRequest toDomain() {
		return SupportRequest.restore(id, userId, subject, message, contactEmail, imageUrl, category, status,
			adminReply, repliedBy, repliedAt, createdAt);
	}
}
