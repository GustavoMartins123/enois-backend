package com.socialcoisa.rede.blog.adapters.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import com.socialcoisa.rede.blog.application.port.out.BlogPostRepository;
import com.socialcoisa.rede.blog.domain.BlogPost;

@Repository
class BlogPostPersistenceAdapter implements BlogPostRepository {

	private final SpringDataBlogPostJpaRepository springData;

	BlogPostPersistenceAdapter(SpringDataBlogPostJpaRepository springData) {
		this.springData = springData;
	}

	@Override
	public BlogPost save(BlogPost post) {
		return springData.save(BlogPostJpaEntity.fromDomain(post)).toDomain();
	}

	@Override
	public Optional<BlogPost> findById(String id) {
		return springData.findById(id).map(BlogPostJpaEntity::toDomain);
	}

	@Override
	public List<BlogPost> findByBlog(String blogId, Instant before, int limit) {
		return springData.findByBlogBefore(blogId, before, PageRequest.of(0, limit)).stream()
			.map(BlogPostJpaEntity::toDomain)
			.toList();
	}

	@Override
	public int countByBlog(String blogId) {
		return springData.countByBlogId(blogId);
	}

	@Override
	public void delete(String id) {
		springData.deleteById(id);
	}
}
