package com.socialcoisa.rede.media.application;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriUtils;

import com.socialcoisa.rede.media.application.port.out.MediaAssetRepository;
import com.socialcoisa.rede.media.application.port.out.MediaPublicUrlVerifier;
import com.socialcoisa.rede.media.application.port.out.MediaS3Copier;
import com.socialcoisa.rede.media.application.port.out.MediaSourceDownloader;
import com.socialcoisa.rede.media.domain.MediaAsset;

/**
 * Migracao ImageKit -> S3 em duas fases (ver docs/MIDIA-S3.md).
 *
 * Fase 1 (copia): baixa o original, sobe para o S3 e confere o objeto pelo HeadObject. Grava
 * s3_cdn_url, mas **nao** troca o cdn_url: o app continua lendo o ImageKit e o objeto antigo fica
 * preservado. Falha nunca apaga nada — incrementa tentativas e tenta de novo num lote futuro.
 *
 * Fase 2 (flip): so com `rede.media.migration.flip-enabled=true` e SOMENTE depois de a URL publica
 * do S3 responder. Sem essa confirmacao, o cdn_url fica como esta: nenhum post perde midia.
 */
@Service
@ConditionalOnProperty(name = "rede.media.migration.enabled", havingValue = "true")
public class MediaMigrationService {

	private static final Logger log = LoggerFactory.getLogger(MediaMigrationService.class);

	/** Upload que nunca chegou ao ImageKit (PENDING antigo) nao deve ocupar o lote para sempre. */
	private static final Duration MIN_AGE = Duration.ofHours(1);
	/** Depois disso, para de tentar e deixa registrado em s3_last_error. */
	private static final int MAX_ATTEMPTS = 10;
	private static final String IMAGEKIT_FALLBACK = "https://ik.imagekit.io/socialcoisa";

	private final MediaAssetRepository repository;
	private final MediaSourceDownloader downloader;
	private final MediaS3Copier copier;
	private final MediaPublicUrlVerifier verifier;
	private final Clock clock;
	private final String imageKitBase;
	private final boolean flipEnabled;

	public MediaMigrationService(MediaAssetRepository repository, MediaSourceDownloader downloader,
			MediaS3Copier copier, MediaPublicUrlVerifier verifier, Clock clock,
			@Value("${rede.media.imagekit.url-endpoint:}") String imageKitBase,
			@Value("${rede.media.migration.flip-enabled:false}") boolean flipEnabled) {
		this.repository = repository;
		this.downloader = downloader;
		this.copier = copier;
		this.verifier = verifier;
		this.clock = clock;
		this.imageKitBase = StringUtils.hasText(imageKitBase) ? trimSlash(imageKitBase.trim()) : IMAGEKIT_FALLBACK;
		this.flipEnabled = flipEnabled;
	}

	public record MigrationResult(int copied, int flipped) {
	}

	/** Uma rodada do worker: copia o lote e, se o flip estiver liberado, relinka o lote. */
	public MigrationResult runOnce(int copyLimit, int flipLimit) {
		int copied = copyBatch(copyLimit);
		int flipped = flipEnabled ? flipBatch(flipLimit) : 0;
		return new MigrationResult(copied, flipped);
	}

	public int copyBatch(int limit) {
		List<MediaAsset> assets = repository.findForS3Copy(limit, clock.instant().minus(MIN_AGE), MAX_ATTEMPTS);
		int copied = 0;
		for (MediaAsset asset : assets) {
			if (!asset.needsS3Copy()) {
				continue;
			}
			Path source = null;
			try {
				source = downloader.download(sourceUrl(asset));
				String s3Url = copier.copy(s3Key(asset.objectKey()), source, asset.mimeType());
				asset.markS3Copy(s3Url, clock.instant());
				repository.save(asset);
				copied++;
			}
			catch (Exception exception) {
				asset.markS3MigrationFailure(exception.getMessage(), clock.instant());
				repository.save(asset);
				log.warn("Falha ao copiar a midia {} para o S3: {}", asset.id(), exception.getMessage());
			}
			finally {
				if (source != null) {
					try {
						Files.deleteIfExists(source);
					}
					catch (Exception ignored) {
					}
				}
			}
		}
		return copied;
	}

	public int flipBatch(int limit) {
		if (!flipEnabled) {
			return 0;
		}
		int flipped = 0;
		for (MediaAsset asset : repository.findForS3Flip(limit)) {
			if (!asset.canActivateS3()) {
				continue;
			}
			if (verifier.verify(asset.s3CdnUrl(), asset.mimeType())) {
				asset.activateS3();
				repository.save(asset);
				flipped++;
			}
			else {
				// CDN fora/403: mantem o ImageKit e tenta de novo na proxima rodada.
				log.warn("S3 ainda nao responde para a midia {}; mantendo o provedor antigo", asset.id());
			}
		}
		return flipped;
	}

	/**
	 * URL de origem do download: o cdn_url relativo ganha o endpoint do ImageKit; `%2F` vira barra
	 * (o CDN do ImageKit aceita o caminho decodificado) e video/audio pedem o ORIGINAL, senao a
	 * cota de transformacao de video responde 403.
	 */
	String sourceUrl(MediaAsset asset) {
		String raw = asset.cdnUrl();
		String absolute = raw.startsWith("http://") || raw.startsWith("https://") ? raw
			: imageKitBase + (raw.startsWith("/") ? "" : "/") + raw;
		absolute = absolute.replace("%2F", "/").replace("%2f", "/");
		if (isAudioOrVideo(asset.mimeType()) && hostIsImageKit(absolute) && !absolute.contains("tr=")) {
			absolute += (absolute.contains("?") ? "&" : "?") + "tr=orig-true";
		}
		return absolute;
	}

	/** A chave no S3 usa barras de verdade (o objectKey do ImageKit vem com `%2F`). */
	static String s3Key(String objectKey) {
		try {
			return UriUtils.decode(objectKey, StandardCharsets.UTF_8);
		}
		catch (RuntimeException exception) {
			return objectKey;
		}
	}

	private static boolean isAudioOrVideo(String mimeType) {
		return mimeType != null && (mimeType.startsWith("video/") || mimeType.startsWith("audio/"));
	}

	private static boolean hostIsImageKit(String url) {
		try {
			return new java.net.URI(url).getHost() != null
				&& new java.net.URI(url).getHost().endsWith("imagekit.io");
		}
		catch (Exception exception) {
			return false;
		}
	}

	private static String trimSlash(String value) {
		String trimmed = value;
		while (trimmed.endsWith("/")) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		return trimmed;
	}
}
