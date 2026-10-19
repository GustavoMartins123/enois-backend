package com.socialcoisa.rede.media.application.port.out;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.socialcoisa.rede.media.domain.MediaAsset;
import com.socialcoisa.rede.media.domain.MediaStorageProvider;

public interface MediaAssetRepository {

	MediaAsset save(MediaAsset asset);

	Optional<MediaAsset> findById(String id);

	List<MediaAsset> findByIds(Collection<String> ids);

	/** Proximo lote da fase 1 (copiar para o S3), priorizando quem errou menos vezes. */
	List<MediaAsset> findForS3Copy(int limit, Instant before, int maxAttempts);

	/** Proximo lote da fase 2 (flip): copia pronta e cdn_url ainda no provedor antigo. */
	List<MediaAsset> findForS3Flip(int limit);

	long countByStorageProvider(MediaStorageProvider provider);

	long countMigratedToS3();

	long countWithMigrationError();

	/** Ultimo erro registrado (ex.: AccessDenied do S3), para diagnostico pelo status publico. */
	Optional<String> lastMigrationError();

	long countActiveOnS3();

	long countAll();
}
