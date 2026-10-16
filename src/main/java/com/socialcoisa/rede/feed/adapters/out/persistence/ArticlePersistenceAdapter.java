package com.socialcoisa.rede.feed.adapters.out.persistence;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.socialcoisa.rede.feed.application.port.out.ArticleRepository;
import com.socialcoisa.rede.feed.domain.PostId;

@Component
class ArticlePersistenceAdapter implements ArticleRepository {

	private final SpringDataArticleJpaRepository repository;

	ArticlePersistenceAdapter(SpringDataArticleJpaRepository repository) {
		this.repository = repository;
	}

	@Override
	@Transactional
	public void save(PostId postId, String body, Instant createdAt) {
		repository.save(ArticleJpaEntity.of(postId.value(), body, createdAt));
	}

	@Override
	@Transactional
	public void updateBody(PostId postId, String body, Instant editedAt) {
		repository.findById(postId.value()).ifPresent(entity -> entity.changeBody(body, editedAt));
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<String> findBody(PostId postId) {
		return repository.findById(postId.value()).map(ArticleJpaEntity::body);
	}
}
