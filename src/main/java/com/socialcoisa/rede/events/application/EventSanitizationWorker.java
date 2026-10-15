package com.socialcoisa.rede.events.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sanitizacao periodica dos eventos vencidos.
 *
 * Roda no boot (o `@Scheduled` sem `initialDelay` dispara junto com a aplicacao) e depois a cada
 * intervalo configurado, para nao depender de alguem lembrar de limpar: passado o evento, as
 * presencas (dado pessoal de quem confirmou) somem e fica apenas o total agregado.
 */
@Component
class EventSanitizationWorker {

	private static final Logger LOG = LoggerFactory.getLogger(EventSanitizationWorker.class);

	private final EventService eventService;

	EventSanitizationWorker(EventService eventService) {
		this.eventService = eventService;
	}

	@Scheduled(fixedDelayString = "${rede.events.sanitize-delay-ms:3600000}")
	void sanitize() {
		try {
			EventService.SanitizationResult result = eventService.sanitizeExpired();
			if (result.sanitized() > 0 || result.purged() > 0) {
				LOG.info("Eventos sanitizados: {} (presencas apagadas); expurgados por retencao: {}",
					result.sanitized(), result.purged());
			}
		}
		catch (RuntimeException exception) {
			LOG.warn("Falha na sanitizacao de eventos: {}", exception.getMessage());
		}
	}
}
