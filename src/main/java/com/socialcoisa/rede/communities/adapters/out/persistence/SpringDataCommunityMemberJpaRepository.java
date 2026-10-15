package com.socialcoisa.rede.communities.adapters.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataCommunityMemberJpaRepository extends JpaRepository<CommunityMemberJpaEntity, Long> {

	boolean existsByCommunityIdAndUserId(String communityId, String userId);

	void deleteByCommunityIdAndUserId(String communityId, String userId);

	void deleteByCommunityId(String communityId);

	int countByCommunityId(String communityId);

	List<CommunityMemberJpaEntity> findByCommunityId(String communityId);

	List<CommunityMemberJpaEntity> findByUserId(String userId);

	Optional<CommunityMemberJpaEntity> findByCommunityIdAndUserId(String communityId, String userId);

}
