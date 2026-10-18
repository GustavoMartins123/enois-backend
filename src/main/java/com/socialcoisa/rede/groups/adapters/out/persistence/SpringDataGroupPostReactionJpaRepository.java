package com.socialcoisa.rede.groups.adapters.out.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataGroupPostReactionJpaRepository extends JpaRepository<GroupReactionJpaEntity, String> {

	Optional<GroupReactionJpaEntity> findByPostIdAndUserId(String postId, String userId);

	void deleteByPostIdAndUserId(String postId, String userId);

	long countByPostId(String postId);
}
