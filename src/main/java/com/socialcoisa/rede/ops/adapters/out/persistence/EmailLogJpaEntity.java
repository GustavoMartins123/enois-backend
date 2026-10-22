package com.socialcoisa.rede.ops.adapters.out.persistence;

import java.time.Instant;

import com.socialcoisa.rede.ops.domain.EmailLog;
import com.socialcoisa.rede.ops.domain.EmailLogStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "email_log", indexes = @Index(name = "idx_email_log_created", columnList = "created_at"))
class EmailLogJpaEntity {

	@Id
	private String id;

	@Column(name = "to_email", nullable = false, length = 180)
	private String toEmail;

	@Column(nullable = false, length = 200)
	private String subject;

	@Column(nullable = false, length = 40)
	private String kind;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private EmailLogStatus status;

	@Column(length = 500)
	private String error;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected EmailLogJpaEntity() {
	}

	static EmailLogJpaEntity fromDomain(EmailLog log) {
		EmailLogJpaEntity entity = new EmailLogJpaEntity();
		entity.id = log.id();
		entity.toEmail = log.toEmail();
		entity.subject = log.subject();
		entity.kind = log.kind();
		entity.status = log.status();
		entity.error = log.error();
		entity.createdAt = log.createdAt();
		return entity;
	}

	EmailLog toDomain() {
		return new EmailLog(id, toEmail, subject, kind, status, error, createdAt);
	}
}
