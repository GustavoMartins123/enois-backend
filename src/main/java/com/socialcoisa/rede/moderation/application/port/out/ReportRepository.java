package com.socialcoisa.rede.moderation.application.port.out;

import java.util.List;
import java.util.Optional;

import com.socialcoisa.rede.moderation.domain.Report;
import com.socialcoisa.rede.moderation.domain.ReportStatus;

public interface ReportRepository {

	Report save(Report report);

	/** Denuncias PENDING ainda sem palpite de IA (para o triage agendado). */
	List<com.socialcoisa.rede.moderation.domain.Report> findPendingWithoutAiTriage(int limit);

	Optional<Report> findById(String id);

	List<Report> findByStatus(ReportStatus status, int limit);

	boolean existsByReporterAndTarget(String reporterId, String targetType, String targetId);

	/** Denuncias do proprio usuario (area de acompanhamento), mais recentes primeiro. */
	List<Report> findByReporter(String reporterId, int limit);
}
