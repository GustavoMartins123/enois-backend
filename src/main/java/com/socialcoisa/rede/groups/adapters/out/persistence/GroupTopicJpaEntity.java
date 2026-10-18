package com.socialcoisa.rede.groups.adapters.out.persistence;

import java.time.Instant;

import com.socialcoisa.rede.groups.domain.GroupTopic;
import com.socialcoisa.rede.groups.domain.GroupTopicStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "group_topics")
class GroupTopicJpaEntity {

	@Id
	private String id;

	@Column(name = "group_id", nullable = false)
	private String groupId;

	@Column(nullable = false, length = 80)
	private String name;

	@Column(length = 300)
	private String description;

	@Column(nullable = false)
	private int position;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private GroupTopicStatus status;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected GroupTopicJpaEntity() {
	}

	static GroupTopicJpaEntity fromDomain(GroupTopic topic) {
		GroupTopicJpaEntity entity = new GroupTopicJpaEntity();
		entity.id = topic.id();
		entity.groupId = topic.groupId();
		entity.name = topic.name();
		entity.description = topic.description();
		entity.position = topic.position();
		entity.status = topic.status();
		entity.createdAt = topic.createdAt();
		entity.updatedAt = topic.updatedAt();
		return entity;
	}

	GroupTopic toDomain() {
		return GroupTopic.restore(id, groupId, name, description, position, status, createdAt, updatedAt);
	}

	boolean belongsTo(String expectedGroupId) {
		return expectedGroupId.equals(groupId);
	}
}
