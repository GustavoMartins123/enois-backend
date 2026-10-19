package com.socialcoisa.rede.moderation.adapters.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.socialcoisa.rede.moderation.domain.ModeratorApplicationStatus;

interface SpringDataModeratorApplicationJpaRepository
		extends JpaRepository<ModeratorApplicationJpaEntity, String> {

	Optional<ModeratorApplicationJpaEntity> findFirstByUserIdOrderByCreatedAtDesc(String userId);

	Optional<ModeratorApplicationJpaEntity> findFirstByUserIdAndStatusOrderByCreatedAtDesc(String userId,
			ModeratorApplicationStatus status);

	boolean existsByUserIdAndStatus(String userId, ModeratorApplicationStatus status);

	List<ModeratorApplicationJpaEntity> findByStatusOrderByCreatedAtAsc(ModeratorApplicationStatus status,
			Pageable pageable);
}
