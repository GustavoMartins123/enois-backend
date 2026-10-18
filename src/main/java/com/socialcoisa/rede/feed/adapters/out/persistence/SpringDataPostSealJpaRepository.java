package com.socialcoisa.rede.feed.adapters.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.socialcoisa.rede.feed.domain.PostSealType;

interface SpringDataPostSealJpaRepository extends JpaRepository<PostSealJpaEntity, String> {

	List<PostSealJpaEntity> findByPostIdAndUserId(String postId, String userId);

	Optional<PostSealJpaEntity> findByPostIdAndUserIdAndSeal(String postId, String userId, PostSealType seal);

	@Modifying
	@Query("DELETE FROM PostSealJpaEntity s WHERE s.postId = :postId AND s.userId = :userId AND s.seal = :seal")
	int deleteByPostAndUserAndSeal(@Param("postId") String postId, @Param("userId") String userId,
			@Param("seal") PostSealType seal);

	@Modifying
	@Query("DELETE FROM PostSealJpaEntity s WHERE s.postId = :postId AND s.userId = :userId")
	int deleteByPostAndUser(@Param("postId") String postId, @Param("userId") String userId);

	List<PostSealJpaEntity> findByPostIdIn(Collection<String> postIds);

	List<PostSealJpaEntity> findByPostIdOrderByCreatedAtAsc(String postId);
}
