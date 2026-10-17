package com.socialcoisa.rede.feed.adapters.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.socialcoisa.rede.feed.application.port.out.PostSealRepository;
import com.socialcoisa.rede.feed.domain.PostSeal;

@Repository
class PostSealPersistenceAdapter implements PostSealRepository {

	private final SpringDataPostSealJpaRepository springData;

	PostSealPersistenceAdapter(SpringDataPostSealJpaRepository springData) {
		this.springData = springData;
	}

	@Override
	public Optional<PostSeal> findByPostAndUser(String postId, String userId) {
		return springData.findByPostIdAndUserId(postId, userId).map(PostSealJpaEntity::toDomain);
	}

	@Override
	public PostSeal save(PostSeal seal) {
		return springData.save(PostSealJpaEntity.fromDomain(seal)).toDomain();
	}

	@Override
	public int delete(String postId, String userId) {
		return springData.deleteByPostAndUser(postId, userId);
	}

	@Override
	public List<PostSeal> findByPostIds(List<String> postIds) {
		return springData.findByPostIdIn(postIds).stream().map(PostSealJpaEntity::toDomain).toList();
	}
}
