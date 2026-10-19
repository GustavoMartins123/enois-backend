package com.socialcoisa.rede.feed.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** A cada 30s olha o relógio: o que venceu vira post pelo caso de uso normal (ver o service). */
@Component
class ScheduledPostPublishWorker {

	private static final Logger LOG = LoggerFactory.getLogger(ScheduledPostPublishWorker.class);

	private final ScheduledPostService service;

	ScheduledPostPublishWorker(ScheduledPostService service) {
		this.service = service;
	}

	@Scheduled(fixedDelayString = "${rede.feed.schedule-delay-ms:30000}")
	void publishDue() {
		try {
			int published = service.publishDue();
			if (published > 0) {
				LOG.info("Posts agendados publicados: {}", published);
			}
		}
		catch (RuntimeException error) {
			LOG.warn("Falha na publicacao de posts agendados: {}", error.getMessage());
		}
	}
}
