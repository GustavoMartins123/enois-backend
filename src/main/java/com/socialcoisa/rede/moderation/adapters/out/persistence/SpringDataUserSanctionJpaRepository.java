package com.socialcoisa.rede.moderation.adapters.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.socialcoisa.rede.moderation.domain.SanctionStatus;

interface SpringDataUserSanctionJpaRepository extends JpaRepository<UserSanctionJpaEntity, String> {

	Optional<UserSanctionJpaEntity> findFirstByUserIdAndStatusOrderByCreatedAtDesc(String userId, SanctionStatus status);

	List<UserSanctionJpaEntity> findTop20ByUserIdOrderByCreatedAtDesc(String userId);
}
