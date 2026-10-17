package com.socialcoisa.rede.blog.adapters.out.persistence;

import java.time.Instant;

import com.socialcoisa.rede.blog.domain.Blog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "blogs")
class BlogJpaEntity {

	@Id
	private String id;

	@Column(name = "user_id", nullable = false)
	private String userId;

	@Column(nullable = false, length = 80)
	private String slug;

	@Column(nullable = false, length = 160)
	private String title;

	@Column(length = 500)
	private String description;

	/** HTML/CSS sanitizado que vira a cara do blog; nulo = cabecalho padrao. */
	@Column(name = "theme_html")
	private String themeHtml;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected BlogJpaEntity() {
	}

	static BlogJpaEntity fromDomain(Blog blog) {
		BlogJpaEntity e = new BlogJpaEntity();
		e.id = blog.id();
		e.userId = blog.userId();
		e.slug = blog.slug();
		e.title = blog.title();
		e.description = blog.description();
		e.themeHtml = blog.themeHtml();
		e.createdAt = blog.createdAt();
		e.updatedAt = blog.updatedAt();
		return e;
	}

	Blog toDomain() {
		return Blog.restore(id, userId, slug, title, description, themeHtml, createdAt, updatedAt);
	}
}
