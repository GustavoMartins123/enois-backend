package com.socialcoisa.rede.moderation.adapters.out.persistence;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.socialcoisa.rede.moderation.domain.ReportStatus;
import com.socialcoisa.rede.moderation.domain.TargetType;

interface SpringDataReportJpaRepository extends JpaRepository<ReportJpaEntity, String> {

	List<ReportJpaEntity> findByStatusAndAiTriagedAtIsNullOrderByCreatedAtAsc(ReportStatus status, Pageable pageable);

	List<ReportJpaEntity> findByStatusOrderByCreatedAtDesc(ReportStatus status);

	List<ReportJpaEntity> findByReporterIdOrderByCreatedAtDesc(String reporterId, Pageable pageable);

	boolean existsByReporterIdAndTargetTypeAndTargetId(String reporterId, TargetType targetType, String targetId);
}
