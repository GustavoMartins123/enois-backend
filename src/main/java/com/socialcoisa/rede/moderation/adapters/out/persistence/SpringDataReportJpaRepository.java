package com.socialcoisa.rede.moderation.adapters.out.persistence;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.socialcoisa.rede.moderation.domain.ReportStatus;
import com.socialcoisa.rede.moderation.domain.TargetType;

interface SpringDataReportJpaRepository extends JpaRepository<ReportJpaEntity, String> {

	List<ReportJpaEntity> findByStatusAndAiTriagedAtIsNullOrderByCreatedAtAsc(ReportStatus status, Pageable pageable);

	List<ReportJpaEntity> findByStatusAndPreTriagedAtIsNullOrderByCreatedAtAsc(ReportStatus status, Pageable pageable);

	long countByTargetTypeAndTargetIdAndCreatedAtAfter(TargetType targetType, String targetId,
			java.time.Instant since);

	List<ReportJpaEntity> findByStatusAndTargetTypeAndPreTriageScoreGreaterThanEqualOrderByPreTriageScoreDesc(
			ReportStatus status, TargetType targetType, int minScore, Pageable pageable);

	List<ReportJpaEntity> findByStatusOrderByCreatedAtDesc(ReportStatus status);

	List<ReportJpaEntity> findByReporterIdOrderByCreatedAtDesc(String reporterId, Pageable pageable);

	boolean existsByReporterIdAndTargetTypeAndTargetId(String reporterId, TargetType targetType, String targetId);
}
