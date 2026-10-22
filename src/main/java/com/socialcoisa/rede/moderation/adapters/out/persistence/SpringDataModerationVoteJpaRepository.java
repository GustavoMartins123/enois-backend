package com.socialcoisa.rede.moderation.adapters.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataModerationVoteJpaRepository extends JpaRepository<ModerationVoteJpaEntity, String> {

	Optional<ModerationVoteJpaEntity> findByReportIdAndModeratorId(String reportId, String moderatorId);

	List<ModerationVoteJpaEntity> findByReportId(String reportId);
}
