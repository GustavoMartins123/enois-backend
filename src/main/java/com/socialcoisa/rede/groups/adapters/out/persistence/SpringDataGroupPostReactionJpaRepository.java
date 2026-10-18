package com.socialcoisa.rede.groups.adapters.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataGroupPostReactionJpaRepository extends JpaRepository<GroupReactionJpaEntity, String> {

	Optional<GroupReactionJpaEntity> findByPostIdAndUserId(String postId, String userId);

	List<GroupReactionJpaEntity> findByUserIdAndPostIdIn(String userId, Collection<String> postIds);

	void deleteByPostIdAndUserId(String postId, String userId);

	long countByPostId(String postId);
}
