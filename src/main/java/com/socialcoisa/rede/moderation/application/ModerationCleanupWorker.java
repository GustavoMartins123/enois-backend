package com.socialcoisa.rede.moderation.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.socialcoisa.rede.moderation.application.port.out.ReportRepository;
import com.socialcoisa.rede.moderation.domain.Report;
import com.socialcoisa.rede.moderation.domain.ReportStatus;
import com.socialcoisa.rede.shared.sanctions.SanctionGateReader;

/**
 * Saneamento da fila de denúncias.
 *
 * <p>Denúncia contra autor já BANIDO não precisa de votação: a decisão já foi tomada. O mesmo
 * vale para denúncia cujo alvo não existe mais (post/conta excluída) — o ticket relatou que
 * contas removidas continuavam aparecendo na tela de avaliação. Aqui essas denúncias são
 * encerradas (DISMISSED) e somem do painel; roda no boot e de meia em meia hora.</p>
 */
@Component
public class ModerationCleanupWorker {

	private static final Logger LOG = LoggerFactory.getLogger(ModerationCleanupWorker.class);

	private static final int LIMIT = 200;

	private final ReportRepository reports;
	private final ModerationService moderation;
	private final SanctionGateReader sanctionGate;

	public ModerationCleanupWorker(ReportRepository reports, ModerationService moderation,
			SanctionGateReader sanctionGate) {
		this.reports = reports;
		this.moderation = moderation;
		this.sanctionGate = sanctionGate;
	}

	@Scheduled(initialDelayString = "${rede.moderation.cleanup-initial-delay-ms:120000}",
		fixedDelayString = "${rede.moderation.cleanup-delay-ms:1800000}")
	@Transactional
	public void closeReportsAgainstBannedOrRemovedUsers() {
		int closed = 0;
		for (Report report : reports.findByStatus(ReportStatus.PENDING, LIMIT)) {
			try {
				ModerationService.ReportReview review = moderation.review(report.id());
				boolean banned = review.authorId() != null && sanctionGate.banned(review.authorId());
				boolean orphan = review.authorId() == null && review.content() == null;
				if (!banned && !orphan) {
					continue;
				}
				report.dismiss("sistema", banned
					? "Autor banido — denúncia encerrada automaticamente"
					: "Alvo removido — denúncia encerrada automaticamente");
				reports.save(report);
				closed++;
			}
			catch (RuntimeException perReport) {
				LOG.warn("Não foi possível saneiar a denúncia {}: {}", report.id(), perReport.getMessage());
			}
		}
		if (closed > 0) {
			LOG.info("Moderação: {} denúncia(s) encerradas por autor banido/alvo removido.", closed);
		}
	}
}
