package com.socialcoisa.rede.moderation.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Triagem de denuncias por IA, a cada 60s (denuncias PENDING sem palpite).
 *
 * Duas fases, com flags independentes: (1) triar — grava o palpite estruturado; (2) aplicar a
 * penalidade quando o veredito PENALIZAR passa no gate (violacao confirmada, sem ambiguidade,
 * confianca >= 0.7). UMA denuncia comprovada ja basta para penalizar; o duvidoso vai para revisao
 * humana. O gate fica no parse (fail-safe) e o banimento NUNCA e automatico — ele so acontece
 * quando um humano NEGA o recurso (SanctionService.denyAppeal).
 */
@Component
public class ReportAiTriageScheduler {

	private static final Logger LOG = LoggerFactory.getLogger(ReportAiTriageScheduler.class);

	private final ModerationAiService moderationAiService;
	private final com.socialcoisa.rede.moderation.application.AiVerdictApplicationService verdictApplication;
	private final boolean enabled;
	private final boolean autoPenaltyEnabled;

	public ReportAiTriageScheduler(ModerationAiService moderationAiService,
			com.socialcoisa.rede.moderation.application.AiVerdictApplicationService verdictApplication,
			@Value("${rede.moderation.ai.triage-enabled:true}") boolean enabled,
			@Value("${rede.moderation.ai.auto-penalty-enabled:true}") boolean autoPenaltyEnabled) {
		this.moderationAiService = moderationAiService;
		this.verdictApplication = verdictApplication;
		this.enabled = enabled;
		this.autoPenaltyEnabled = autoPenaltyEnabled;
	}

	@Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
	public void triagePending() {
		if (!enabled || !moderationAiService.available()) {
			return;
		}
		try {
			int triaged = moderationAiService.triagePendingReports(10);
			if (triaged > 0) {
				LOG.info("Denuncias triadas por IA: {}", triaged);
			}
			if (autoPenaltyEnabled && triaged >= 0) {
				int penalized = verdictApplication.applyPendingVerdicts(10);
				if (penalized > 0) {
					LOG.info("Denuncias com penalidade automatica aplicada: {}", penalized);
				}
			}
		}
		catch (RuntimeException failure) {
			LOG.warn("Falha na triagem por IA (as denuncias seguem para revisao humana).");
		}
	}
}
