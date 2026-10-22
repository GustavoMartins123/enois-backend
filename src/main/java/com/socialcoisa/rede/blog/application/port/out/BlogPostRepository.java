package com.socialcoisa.rede.blog.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.socialcoisa.rede.blog.domain.BlogPost;

public interface BlogPostRepository {

	BlogPost save(BlogPost post);

	Optional<BlogPost> findById(String id);

	List<BlogPost> findByBlog(String blogId, Instant before, int limit);

	int countByBlog(String blogId);

	void delete(String id);
}
