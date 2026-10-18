package com.socialcoisa.rede.messaging.adapters.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Mensagem que uma pessoa apagou para si (o outro participante continua vendo). */
@Entity
@Table(name = "message_hidden")
class MessageHiddenJpaEntity {

	@Id
	private String id;

	@Column(name = "user_id", nullable = false)
	private String userId;

	@Column(name = "message_id", nullable = false)
	private String messageId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected MessageHiddenJpaEntity() {
	}

	static MessageHiddenJpaEntity of(String id, String userId, String messageId, Instant createdAt) {
		MessageHiddenJpaEntity entity = new MessageHiddenJpaEntity();
		entity.id = id;
		entity.userId = userId;
		entity.messageId = messageId;
		entity.createdAt = createdAt;
		return entity;
	}
}
