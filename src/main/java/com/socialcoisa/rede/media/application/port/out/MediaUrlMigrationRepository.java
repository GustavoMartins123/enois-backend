package com.socialcoisa.rede.media.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Estado persistido de UMA URL legada em migração (idempotência entre réplicas e retries). */
public interface MediaUrlMigrationRepository {

	enum Status {
		/** Objeto já copiado; falta a URL pública responder para ativar e trocar referências. */
		COPIED,
		/** Copiado e verificado: referências podem usar a URL do S3. */
		ACTIVE,
		/** Aguardando/em tentativa. */
		PENDING,
		/** Falhou (com motivo); novas tentativas até o teto. */
		FAILED
	}

	record Row(String urlHash, String sourceUrl, Status status, String s3Url, int attempts, String lastError,
			Instant claimedAt, Instant updatedAt) {
	}

	Optional<Row> findByHash(String urlHash);

	/** Cria a linha se não existir (idempotente). */
	void ensurePending(String urlHash, String sourceUrl, Instant now);

	/** Claim atômico: ganha quem gravar; claim velho expira. */
	boolean claim(String urlHash, Instant now, Instant staleBefore, int maxAttempts);

	void markCopied(String urlHash, String s3Url, Instant now);

	void markActive(String urlHash, Instant now);

	void markFailed(String urlHash, String error, Instant now);

	/** Linhas copiadas que ainda não foram verificadas/ativadas (fase de flip). */
	List<Row> findForFlip(int limit);
}
