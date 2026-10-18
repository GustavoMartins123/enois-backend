package com.socialcoisa.rede.feed.adapters.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataReactionJpaRepository extends JpaRepository<ReactionJpaEntity, String> {

	@org.springframework.data.jpa.repository.Query(
		"SELECT COUNT(r) FROM ReactionJpaEntity r JOIN PostJpaEntity p ON r.postId = p.id "
		+ "WHERE p.authorId = :authorId AND r.userId <> :authorId")
	long countReceivedExcludingSelf(@org.springframework.data.repository.query.Param("authorId") String authorId);

	@org.springframework.data.jpa.repository.Query(
		"SELECT COUNT(r) FROM ReactionJpaEntity r JOIN PostJpaEntity p ON r.postId = p.id "
		+ "WHERE p.authorId = :authorId AND r.userId <> :authorId AND r.createdAt >= :since")
	long countReceivedSince(@org.springframework.data.repository.query.Param("authorId") String authorId,
		@org.springframework.data.repository.query.Param("since") java.time.Instant since);

	boolean existsByPostIdAndUserId(String postId, String userId);

	Optional<ReactionJpaEntity> findByPostIdAndUserId(String postId, String userId);

	List<ReactionJpaEntity> findByPostIdInAndUserId(Collection<String> postIds, String userId);

	List<ReactionJpaEntity> findByPostIdOrderByCreatedAtDesc(String postId);
}
