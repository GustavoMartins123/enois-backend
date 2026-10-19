package com.socialcoisa.rede.media.adapters.out.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import com.socialcoisa.rede.media.application.port.out.MediaPublicUrlVerifier;
import com.socialcoisa.rede.media.application.port.out.MediaS3Copier;
import com.socialcoisa.rede.media.application.port.out.MediaSourceDownloader;

/**
 * Peças da migracao ImageKit -> S3. Existe mesmo com `rede.media.storage=imagekit` (e o caso da
 * migracao): a copia usa o MESMO bucket/regiao/prefixo do storage novo, mas nao troca o provedor
 * dos uploads. Credencial resolve pela cadeia padrao do SDK no pod (IRSA).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "rede.media.migration.enabled", havingValue = "true")
class MediaMigrationConfig {

	/**
	 * Executor da migração OPORTUNISTA (fila limitada, threads daemon): a leitura agenda e segue;
	 * fila cheia simplesmente adia — a varredura em lote cobre depois. 2 threads bastam: o trabalho
	 * é I/O de rede e o claim no banco já impede duplicação.
	 */
	@Bean(destroyMethod = "shutdown")
	java.util.concurrent.ExecutorService mediaLazyMigrationExecutor() {
		return new java.util.concurrent.ThreadPoolExecutor(2, 2, 0L, java.util.concurrent.TimeUnit.MILLISECONDS,
			new java.util.concurrent.ArrayBlockingQueue<>(500),
			runnable -> {
				Thread thread = new Thread(runnable, "midia-migracao-oportunista");
				thread.setDaemon(true);
				return thread;
			},
			new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
	}

	@Bean
	S3Client mediaMigrationS3Client(@Value("${rede.media.s3.region:us-east-1}") String region) {
		return S3Client.builder().region(Region.of(region == null || region.isBlank() ? "us-east-1" : region.trim()))
			.build();
	}

	@Bean
	MediaSourceDownloader mediaSourceDownloader() {
		return new HttpMediaSourceDownloader();
	}

	@Bean
	MediaPublicUrlVerifier mediaPublicUrlVerifier() {
		return new HttpMediaPublicUrlVerifier();
	}

	@Bean
	MediaS3Copier mediaS3Copier(S3Client mediaMigrationS3Client,
			@Value("${rede.media.s3.bucket:}") String bucket,
			@Value("${rede.media.s3.region:us-east-1}") String region,
			@Value("${rede.media.s3.prefix:uploads/}") String prefix,
			@Value("${rede.media.s3.public-base-url:}") String publicBaseUrl) {
		return new S3MediaCopier(mediaMigrationS3Client, bucket, region, prefix, publicBaseUrl);
	}
}
