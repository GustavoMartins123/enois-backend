package com.socialcoisa.rede.communities.adapters.out.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataFavoriteCommunityJpaRepository extends JpaRepository<FavoriteCommunityJpaEntity, String> {

	boolean existsByUserIdAndCommunityId(String userId, String communityId);

	void deleteByUserIdAndCommunityId(String userId, String communityId);

	List<FavoriteCommunityJpaEntity> findByUserIdOrderByCreatedAtDesc(String userId);
}
