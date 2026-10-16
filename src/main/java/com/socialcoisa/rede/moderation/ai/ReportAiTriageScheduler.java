package com.socialcoisa.rede.moderation.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Triagem de denuncias por IA, a cada 60s (denuncias PENDING sem palpite). O palpite e SO
 * orientacao para o painel admin — a penalidade continua sendo decidida por revisao humana.
 */
@Component
public class ReportAiTriageScheduler {

	private static final Logger LOG = LoggerFactory.getLogger(ReportAiTriageScheduler.class);

	private final ModerationAiService moderationAiService;
	private final boolean enabled;

	public ReportAiTriageScheduler(ModerationAiService moderationAiService,
			@Value("${rede.moderation.ai.triage-enabled:true}") boolean enabled) {
		this.moderationAiService = moderationAiService;
		this.enabled = enabled;
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
		}
		catch (RuntimeException failure) {
			LOG.warn("Falha na triagem por IA (as denuncias seguem para revisao humana).");
		}
	}
}
