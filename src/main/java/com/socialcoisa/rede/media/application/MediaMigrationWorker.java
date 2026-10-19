package com.socialcoisa.rede.media.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Rodadas pequenas e frequentes da migracao (o "aos poucos" combinado): nao ha janela de
 * manutencao e o worker nunca derruba a aplicacao — erro vira tentativa registrada no asset.
 *
 * Desligado por padrao; liga com `REDE_MEDIA_MIGRATION_ENABLED=true`. O flip tem interruptor
 * separado (`REDE_MEDIA_MIGRATION_FLIP_ENABLED`) e so roda quando a leitura publica do S3 responde.
 */
@Component
@ConditionalOnProperty(name = "rede.media.migration.enabled", havingValue = "true")
class MediaMigrationWorker {

	private static final Logger log = LoggerFactory.getLogger(MediaMigrationWorker.class);

	private final MediaMigrationService service;
	private final int batchSize;

	MediaMigrationWorker(MediaMigrationService service,
			@Value("${rede.media.migration.batch-size:10}") int batchSize) {
		this.service = service;
		this.batchSize = Math.max(1, Math.min(batchSize, 100));
	}

	@Scheduled(fixedDelayString = "${rede.media.migration.delay-ms:30000}",
		initialDelayString = "${rede.media.migration.initial-delay-ms:45000}")
	void run() {
		try {
			MediaMigrationService.MigrationResult result = service.runOnce(batchSize, batchSize);
			if (result.copied() > 0 || result.flipped() > 0) {
				log.info("Migracao de midia: copiados={}, relinkados={}", result.copied(), result.flipped());
			}
		}
		catch (Exception exception) {
			log.warn("Rodada da migracao falhou: {}", exception.getMessage());
		}
	}
}
