package com.socialcoisa.rede.moderation.adapters.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import com.socialcoisa.rede.moderation.application.port.out.ReportRepository;
import com.socialcoisa.rede.moderation.domain.Report;
import com.socialcoisa.rede.moderation.domain.ReportStatus;
import com.socialcoisa.rede.moderation.domain.TargetType;

@Repository
class ReportPersistenceAdapter implements ReportRepository {

	private final SpringDataReportJpaRepository jpaRepository;

	ReportPersistenceAdapter(SpringDataReportJpaRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	public Report save(Report report) {
		return jpaRepository.save(ReportJpaEntity.fromDomain(report)).toDomain();
	}

	@Override
	public Optional<Report> findById(String id) {
		return jpaRepository.findById(id).map(ReportJpaEntity::toDomain);
	}

	@Override
	public List<Report> findByStatus(ReportStatus status, int limit) {
		return jpaRepository.findByStatusOrderByCreatedAtDesc(status).stream()
			.map(ReportJpaEntity::toDomain)
			.limit(limit)
			.toList();
	}

	@Override
	public List<com.socialcoisa.rede.moderation.domain.Report> findPendingWithoutAiTriage(int limit) {
		return jpaRepository
			.findByStatusAndAiTriagedAtIsNullOrderByCreatedAtAsc(ReportStatus.PENDING, PageRequest.of(0, limit))
			.stream().map(ReportJpaEntity::toDomain).toList();
	}

	@Override
	public List<Report> findPendingWithoutPreTriage(int limit) {
		return jpaRepository
			.findByStatusAndPreTriagedAtIsNullOrderByCreatedAtAsc(ReportStatus.PENDING, PageRequest.of(0, limit))
			.stream().map(ReportJpaEntity::toDomain).toList();
	}

	@Override
	public List<Report> findPendingPostsByPreTriageScore(int minScore, int limit) {
		return jpaRepository
			.findByStatusAndTargetTypeAndPreTriageScoreGreaterThanEqualOrderByPreTriageScoreDesc(
				ReportStatus.PENDING, TargetType.POST, minScore, PageRequest.of(0, Math.max(1, limit)))
			.stream().map(ReportJpaEntity::toDomain).toList();
	}

	@Override
	public long countAgainstTargetSince(String targetType, String targetId, java.time.Instant since) {
		return jpaRepository.countByTargetTypeAndTargetIdAndCreatedAtAfter(TargetType.valueOf(targetType),
			targetId, since);
	}

	@Override
	public List<com.socialcoisa.rede.moderation.domain.Report> findByReporter(String reporterId, int limit) {
		return jpaRepository
			.findByReporterIdOrderByCreatedAtDesc(reporterId, PageRequest.of(0, Math.max(1, limit)))
			.stream().map(ReportJpaEntity::toDomain).toList();
	}

	@Override
	public boolean existsByReporterAndTarget(String reporterId, String targetType, String targetId) {
		return jpaRepository.existsByReporterIdAndTargetTypeAndTargetId(reporterId, TargetType.valueOf(targetType),
			targetId);
	}
}
