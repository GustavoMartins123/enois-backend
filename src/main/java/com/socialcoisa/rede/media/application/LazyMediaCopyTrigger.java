package com.socialcoisa.rede.media.application;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.socialcoisa.rede.media.domain.MediaAsset;

/**
 * Dispara a cópia oportunista de um ASSET lido (grade de mídia do post chama `GET /media/{id}`).
 *
 * <p>Roda fora do caminho crítico: a leitura responde na hora e a cópia acontece no executor com
 * fila limitada. O claim atômico no banco (ver {@link MediaMigrationService#copyOne}) garante que
 * acessos simultâneos — em qualquer réplica — não dupliquem a transferência. Fila cheia não é
 * erro: o lote cobre depois.</p>
 */
@Component
@ConditionalOnProperty(name = "rede.media.migration.enabled", havingValue = "true")
class LazyMediaCopyTrigger {

	private static final Logger log = LoggerFactory.getLogger(LazyMediaCopyTrigger.class);

	private final MediaMigrationService migration;
	private final ExecutorService executor;

	LazyMediaCopyTrigger(MediaMigrationService migration, ExecutorService mediaLazyMigrationExecutor) {
		this.migration = migration;
		this.executor = mediaLazyMigrationExecutor;
	}

	void copySoon(MediaAsset asset) {
		if (asset == null || !asset.needsS3Copy()) {
			return;
		}
		String id = asset.id();
		try {
			executor.execute(() -> {
				try {
					migration.copyOne(id);
				}
				catch (RuntimeException falha) {
					log.warn("Migracao oportunista da midia {} falhou: {}", id, falha.getMessage());
				}
			});
		}
		catch (RejectedExecutionException filaCheia) {
			log.debug("Migracao oportunista da midia {} adiada (fila cheia)", id);
		}
	}
}
