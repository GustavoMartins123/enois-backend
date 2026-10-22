package com.socialcoisa.rede.network.adapters.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataUserBlockJpaRepository extends JpaRepository<UserBlockJpaEntity, Long> {

	Optional<UserBlockJpaEntity> findByBlockerIdAndBlockedId(String blockerId, String blockedId);

	List<UserBlockJpaEntity> findByBlockerIdOrderByCreatedAtDesc(String blockerId);

	List<UserBlockJpaEntity> findByBlockedId(String blockedId);
}