package com.socialcoisa.rede.blog.adapters.out.persistence;

import java.time.Instant;

import com.socialcoisa.rede.blog.domain.BlogSubscription;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "blog_subscriptions")
class BlogSubscriptionJpaEntity {

	@Id
	private String id;

	@Column(name = "blog_id", nullable = false)
	private String blogId;

	@Column(name = "user_id", nullable = false)
	private String userId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected BlogSubscriptionJpaEntity() {
	}

	static BlogSubscriptionJpaEntity fromDomain(BlogSubscription subscription) {
		BlogSubscriptionJpaEntity e = new BlogSubscriptionJpaEntity();
		e.id = subscription.id();
		e.blogId = subscription.blogId();
		e.userId = subscription.userId();
		e.createdAt = subscription.createdAt();
		return e;
	}

	BlogSubscription toDomain() {
		return new BlogSubscription(id, blogId, userId, createdAt);
	}
}
