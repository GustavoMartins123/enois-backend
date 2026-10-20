package com.socialcoisa.rede.moderation.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.socialcoisa.rede.shared.security.AutoReportEvent;

/**
 * Escuta {@link AutoReportEvent} publicados pelos guards de seguranca (BehavioralTimingGuard,
 * UserRateLimiter) e transforma em denuncias automaticas na moderacao.
 *
 * <p>O reporterId e "system" — nenhum usuario real e o denunciante. O report segue o mesmo
 * fluxo de qualquer outro e aparece no painel de moderacao para revisao humana.
 */
@Component
class AutoReportListener {

	private static final Logger LOG = LoggerFactory.getLogger(AutoReportListener.class);
	private static final String SYSTEM_REPORTER = "system";

	private final ModerationService moderationService;

	AutoReportListener(ModerationService moderationService) {
		this.moderationService = moderationService;
	}

	@EventListener
	void onAutoReport(AutoReportEvent event) {
		try {
			moderationService.report(SYSTEM_REPORTER, event.targetType(), event.userId(),
				event.reason(), event.description());
			LOG.info("Auto-report criado para usuario {} (motivo: {})", event.userId(), event.reason());
		} catch (RuntimeException e) {
			// Duplicata (409) ou qualquer outra falha: o guard ja fez o trabalho dele,
			// o report e best-effort.
			LOG.debug("Auto-report ignorado para {}: {}", event.userId(), e.getMessage());
		}
	}
}
