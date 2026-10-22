package com.socialcoisa.rede.blog.adapters.out.persistence;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.socialcoisa.rede.blog.application.port.out.BlogSubscriptionRepository;
import com.socialcoisa.rede.blog.domain.BlogSubscription;

@Repository
class BlogSubscriptionPersistenceAdapter implements BlogSubscriptionRepository {

	private final SpringDataBlogSubscriptionJpaRepository springData;

	BlogSubscriptionPersistenceAdapter(SpringDataBlogSubscriptionJpaRepository springData) {
		this.springData = springData;
	}

	@Override
	public BlogSubscription save(BlogSubscription subscription) {
		return springData.save(BlogSubscriptionJpaEntity.fromDomain(subscription)).toDomain();
	}

	@Override
	public int delete(String blogId, String userId) {
		return springData.deleteByBlogAndUser(blogId, userId);
	}

	@Override
	public boolean exists(String blogId, String userId) {
		return springData.findByBlogIdAndUserId(blogId, userId).isPresent();
	}

	@Override
	public List<String> findSubscriberIds(String blogId) {
		return springData.findSubscriberIds(blogId);
	}

	@Override
	public List<String> findSubscribedBlogIds(String userId) {
		return springData.findSubscribedBlogIds(userId);
	}
}
