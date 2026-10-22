package com.socialcoisa.rede.media.adapters.out.storage;

import java.nio.charset.StandardCharsets;

import org.springframework.web.util.UriUtils;

import com.socialcoisa.rede.media.application.port.out.MediaStorageGateway;
import com.socialcoisa.rede.media.domain.MediaAsset;

/**
 * Exclusão de objeto no S3 (modo de storage novo).
 *
 * <p>A chave é a mesma da migração: o `objectKey` antigo pode vir com `%2F` (o ImageKit guardava
 * a barra codificada), e o S3 usa barras de verdade — decodificar aqui evita apagar "outro"
 * arquivo ou receber 404.</p>
 *
 * <p>`purgeCdn` é no-op: a leitura no S3 é direta no bucket/CDN com o endereço estável; não há
 * invalidação de cache própria. Se um dia houver CloudFront com TTL longo, é aqui que entra.</p>
 */
class S3MediaStorageGateway implements MediaStorageGateway {

	private final software.amazon.awssdk.services.s3.S3Client client;
	private final String bucket;

	S3MediaStorageGateway(software.amazon.awssdk.services.s3.S3Client client, String bucket) {
		this.client = client;
		this.bucket = bucket;
	}

	@Override
	public void purgeCdn(MediaAsset asset) {
		// Sem invalidação de CDN no modo S3 (endereço estável, cache do navegador apenas).
	}

	@Override
	public void deleteObject(MediaAsset asset) {
		client.deleteObject(builder -> builder.bucket(bucket).key(s3Key(asset.objectKey())));
	}

	/** Mesma regra de `MediaMigrationService.s3Key`: barras de verdade, resto preservado. */
	static String s3Key(String objectKey) {
		try {
			return UriUtils.decode(objectKey, StandardCharsets.UTF_8);
		}
		catch (IllegalArgumentException malformed) {
			return objectKey;
		}
	}
}
