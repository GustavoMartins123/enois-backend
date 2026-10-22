package com.socialcoisa.rede.feed.adapters.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.socialcoisa.rede.feed.application.port.out.PostSealRepository;
import com.socialcoisa.rede.feed.domain.PostSeal;
import com.socialcoisa.rede.feed.domain.PostSealType;

@Repository
class PostSealPersistenceAdapter implements PostSealRepository {

	private final SpringDataPostSealJpaRepository springData;

	PostSealPersistenceAdapter(SpringDataPostSealJpaRepository springData) {
		this.springData = springData;
	}

	@Override
	public List<PostSeal> findByPostAndUser(String postId, String userId) {
		return springData.findByPostIdAndUserId(postId, userId).stream().map(PostSealJpaEntity::toDomain).toList();
	}

	@Override
	public Optional<PostSeal> findByPostAndUserAndSeal(String postId, String userId, PostSealType seal) {
		return springData.findByPostIdAndUserIdAndSeal(postId, userId, seal).map(PostSealJpaEntity::toDomain);
	}

	@Override
	public PostSeal save(PostSeal seal) {
		return springData.save(PostSealJpaEntity.fromDomain(seal)).toDomain();
	}

	@Override
	public int delete(String postId, String userId, PostSealType seal) {
		return springData.deleteByPostAndUserAndSeal(postId, userId, seal);
	}

	@Override
	public int deleteAll(String postId, String userId) {
		return springData.deleteByPostAndUser(postId, userId);
	}

	@Override
	public List<PostSeal> findByPostIds(List<String> postIds) {
		return springData.findByPostIdIn(postIds).stream().map(PostSealJpaEntity::toDomain).toList();
	}

	@Override
	public List<PostSeal> findByPostId(String postId) {
		return springData.findByPostIdOrderByCreatedAtAsc(postId).stream().map(PostSealJpaEntity::toDomain).toList();
	}
}
