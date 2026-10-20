package com.socialcoisa.rede.moderation.adapters.out.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataBlockedIpJpaRepository extends JpaRepository<BlockedIpJpaEntity, String> {

	boolean existsByIp(String ip);

	java.util.Optional<BlockedIpJpaEntity> findByIp(String ip);

	List<BlockedIpJpaEntity> findByOrderByCreatedAtDesc(org.springframework.data.domain.Pageable pageable);

	void deleteByIp(String ip);
}
