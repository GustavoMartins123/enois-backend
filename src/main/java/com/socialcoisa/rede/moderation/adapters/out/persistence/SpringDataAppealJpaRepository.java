package com.socialcoisa.rede.moderation.adapters.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.socialcoisa.rede.moderation.domain.AppealStatus;

interface SpringDataAppealJpaRepository extends JpaRepository<AppealJpaEntity, String> {

	Optional<AppealJpaEntity> findById(String id);

	List<AppealJpaEntity> findByStatusOrderByCreatedAtDesc(AppealStatus status, Pageable pageable);

	List<AppealJpaEntity> findTop20ByUserIdOrderByCreatedAtDesc(String userId);

	boolean existsByUserIdAndStatus(String userId, AppealStatus status);
}
