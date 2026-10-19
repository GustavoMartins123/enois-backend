package com.socialcoisa.rede.media.adapters.out.storage;

import java.nio.file.Files;
import java.nio.file.Path;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import com.socialcoisa.rede.media.application.port.out.MediaS3Copier;

/**
 * Copia um objeto para o S3 e CONFIRMA pelo HeadObject que ele chegou com o tamanho do arquivo
 * baixado. So depois disso o servico grava s3_cdn_url; o cdn_url do app continua no provedor antigo
 * ate a fase de flip (que ainda confere a leitura publica).
 */
class S3MediaCopier implements MediaS3Copier {

	private final S3Client s3;
	private final String bucket;
	private final String baseUrl;

	S3MediaCopier(S3Client s3, String bucket, String region, String prefix, String publicBaseUrl) {
		if (bucket == null || bucket.isBlank()) {
			throw new IllegalStateException("rede.media.s3.bucket e obrigatorio para a migracao");
		}
		this.s3 = s3;
		this.bucket = bucket.trim();
		this.baseUrl = new S3StorageUrlFactory(bucket, region, prefix, publicBaseUrl).baseUrl();
	}

	@Override
	public String copy(String objectKey, Path source, String mimeType) {
		try {
			long size = Files.size(source);
			s3.putObject(builder -> builder.bucket(bucket).key(objectKey).contentType(mimeType),
				RequestBody.fromFile(source));
			HeadObjectResponse head = s3.headObject(builder -> builder.bucket(bucket).key(objectKey));
			if (head.contentLength() == null || head.contentLength() != size) {
				throw new IllegalStateException("tamanho no S3 difere do arquivo enviado");
			}
			return baseUrl + objectKey;
		}
		catch (IllegalStateException exception) {
			throw exception;
		}
		catch (Exception exception) {
			throw new IllegalStateException("copia para o S3 falhou: " + exception.getMessage(), exception);
		}
	}
}
