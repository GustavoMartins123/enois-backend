package com.socialcoisa.rede.blog.adapters.out.feed;

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.socialcoisa.rede.blog.application.port.out.BlogRepository;
import com.socialcoisa.rede.blog.application.port.out.BlogSubscriptionRepository;
import com.socialcoisa.rede.feed.application.port.out.SubscribedBlogAuthorsReader;

/** Quem o leitor segue de blog, para o feed somar esses autores no "Para voce". */
@Component
class SubscribedBlogAuthorsAdapter implements SubscribedBlogAuthorsReader {

	private final BlogSubscriptionRepository subscriptions;
	private final BlogRepository blogs;

	SubscribedBlogAuthorsAdapter(BlogSubscriptionRepository subscriptions, BlogRepository blogs) {
		this.subscriptions = subscriptions;
		this.blogs = blogs;
	}

	@Override
	public Set<String> authorsFollowedBy(String userId) {
		Set<String> authors = new LinkedHashSet<>();
		for (String blogId : subscriptions.findSubscribedBlogIds(userId)) {
			blogs.findById(blogId).ifPresent(blog -> authors.add(blog.userId()));
		}
		return authors;
	}
}
