package com.socialcoisa.rede.communities.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataCommunityPinnedPostJpaRepository
		extends JpaRepository<CommunityPinnedPostJpaEntity, String> {
}
