package com.socialcoisa.rede.communities.adapters.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "community_pinned_posts")
class CommunityPinnedPostJpaEntity {

	@Id
	@Column(name = "community_id", nullable = false)
	private String communityId;

	@Column(name = "post_id", nullable = false)
	private String postId;

	@Column(name = "pinned_by", nullable = false)
	private String pinnedBy;

	@Column(name = "pinned_at", nullable = false)
	private Instant pinnedAt;

	protected CommunityPinnedPostJpaEntity() {
	}

	CommunityPinnedPostJpaEntity(String communityId, String postId, String pinnedBy, Instant pinnedAt) {
		this.communityId = communityId;
		this.postId = postId;
		this.pinnedBy = pinnedBy;
		this.pinnedAt = pinnedAt;
	}

	String postId() {
		return postId;
	}
}
