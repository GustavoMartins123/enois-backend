package com.socialcoisa.rede.jobs.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Atualiza as vagas externas todo dia a meia-noite (padrao), em vez de a cada requisicao. Falha na
 * atualizacao nao derruba o app nem as vagas internas.
 */
@Component
public class ExternalJobRefreshScheduler {

	private static final Logger LOG = LoggerFactory.getLogger(ExternalJobRefreshScheduler.class);

	private final ExternalJobImportService importService;
	private final boolean enabled;

	public ExternalJobRefreshScheduler(ExternalJobImportService importService,
			@Value("${rede.jobs.import.external-refresh-enabled:true}") boolean enabled) {
		this.importService = importService;
		this.enabled = enabled;
	}

	/** Cron padrao: 00:00 todos os dias (fuso do servidor). */
	@Scheduled(cron = "${rede.jobs.import.external-refresh-cron:0 0 0 * * *}")
	public void refreshNightly() {
		if (!enabled) {
			return;
		}
		try {
			int touched = importService.refresh();
			LOG.info("Vagas externas atualizadas: {}", touched);
		}
		catch (RuntimeException failure) {
			LOG.warn("Falha ao atualizar vagas externas (segue com o que ja esta no banco).");
		}
	}
}
