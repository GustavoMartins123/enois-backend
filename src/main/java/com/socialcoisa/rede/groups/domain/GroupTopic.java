package com.socialcoisa.rede.groups.domain;

import java.time.Instant;

/**
 * Um tópico interno do grupo: organiza as publicações por assunto.
 *
 * O tópico pertence a EXATAMENTE um grupo, e a publicação verifica que o tópico é daquele grupo —
 * não basta o id existir (ver {@link GroupPost}). Arquivar para de receber publicação sem apagar o
 * que já está lá.
 */
public class GroupTopic {

	private final String id;
	private final String groupId;
	private final String name;
	private final String description;
	private final int position;
	private final GroupTopicStatus status;
	private final Instant createdAt;
	private final Instant updatedAt;

	private GroupTopic(String id, String groupId, String name, String description, int position,
			GroupTopicStatus status, Instant createdAt, Instant updatedAt) {
		this.id = id;
		this.groupId = groupId;
		this.name = name;
		this.description = description;
		this.position = position;
		this.status = status;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	public static GroupTopic create(String id, String groupId, String name, String description, int position,
			Instant now) {
		return new GroupTopic(id, groupId, name, description, position, GroupTopicStatus.OPEN, now, now);
	}

	public static GroupTopic restore(String id, String groupId, String name, String description, int position,
			GroupTopicStatus status, Instant createdAt, Instant updatedAt) {
		return new GroupTopic(id, groupId, name, description, position, status, createdAt, updatedAt);
	}

	/** Nome nulo mantém o atual; descrição nula mantém, vazia limpa. */
	public GroupTopic update(String name, String description, Instant now) {
		return new GroupTopic(id, groupId, name == null ? this.name : name,
			description == null ? this.description : description, position, status, createdAt, now);
	}

	public GroupTopic archive(Instant now) {
		return new GroupTopic(id, groupId, name, description, position, GroupTopicStatus.ARCHIVED, createdAt, now);
	}

	public GroupTopic withPosition(int newPosition, Instant now) {
		return new GroupTopic(id, groupId, name, description, newPosition, status, createdAt, now);
	}

	public boolean acceptsPosts() {
		return status == GroupTopicStatus.OPEN;
	}

	public String id() {
		return id;
	}

	public String groupId() {
		return groupId;
	}

	public String name() {
		return name;
	}

	public String description() {
		return description;
	}

	public int position() {
		return position;
	}

	public GroupTopicStatus status() {
		return status;
	}

	public Instant createdAt() {
		return createdAt;
	}

	public Instant updatedAt() {
		return updatedAt;
	}
}
