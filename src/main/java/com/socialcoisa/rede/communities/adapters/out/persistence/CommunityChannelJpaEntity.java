package com.socialcoisa.rede.communities.adapters.out.persistence;

import java.time.Instant;

import com.socialcoisa.rede.communities.domain.CommunityChannel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "community_channels")
class CommunityChannelJpaEntity {

	@Id
	@Column(name = "id", length = 64, nullable = false)
	private String id;

	@Column(name = "community_id", length = 64, nullable = false)
	private String communityId;

	@Column(name = "name", length = 80, nullable = false)
	private String name;

	@Column(name = "slug", length = 90, nullable = false)
	private String slug;

	@Column(name = "topic", length = 40)
	private String topic;

	@Column(name = "description", length = 300)
	private String description;

	@Column(name = "created_by", length = 64, nullable = false)
	private String createdBy;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected CommunityChannelJpaEntity() {
	}

	static CommunityChannelJpaEntity fromDomain(CommunityChannel channel) {
		CommunityChannelJpaEntity entity = new CommunityChannelJpaEntity();
		entity.id = channel.id();
		entity.communityId = channel.communityId();
		entity.name = channel.name();
		entity.slug = channel.slug();
		entity.topic = channel.topic();
		entity.description = channel.description();
		entity.createdBy = channel.createdBy();
		entity.createdAt = channel.createdAt();
		return entity;
	}

	CommunityChannel toDomain() {
		return new CommunityChannel(id, communityId, name, slug, topic, description, createdBy, createdAt);
	}
}
