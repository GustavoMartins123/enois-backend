package com.socialcoisa.rede.blog.application.port.out;

import java.util.List;

import com.socialcoisa.rede.blog.domain.BlogSubscription;

public interface BlogSubscriptionRepository {

	BlogSubscription save(BlogSubscription subscription);

	/** Remove a inscricao; devolve quantas linhas sairam (0 = nao estava inscrito). */
	int delete(String blogId, String userId);

	boolean exists(String blogId, String userId);

	/** Ids de quem segue o blog — e para eles que a novidade vai. */
	List<String> findSubscriberIds(String blogId);

	/** Ids dos blogs que a pessoa segue. */
	List<String> findSubscribedBlogIds(String userId);
}
