package com.socialcoisa.rede.media.adapters.out.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import com.socialcoisa.rede.media.application.port.out.MediaUploadSigner;
import com.socialcoisa.rede.media.application.port.out.StorageUrlFactory;

/**
 * Storage S3, ligado por `REDE_MEDIA_STORAGE=s3` (ver docs/MIDIA-S3.md).
 *
 * O padrao continua sendo o ImageKit: virar o storage e decisao de operacao, por variavel de
 * ambiente, e voltar atras e trocar a variavel de novo — nenhum codigo muda. Enquanto estiver em
 * `imagekit`, este bloco nem e criado e o app se comporta exatamente como hoje.
 *
 * Credencial nao aparece aqui: o S3Presigner resolve pela cadeia padrao do SDK dentro do pod.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(S3StorageConfig.S3Properties.class)
@ConditionalOnProperty(name = "rede.media.storage", havingValue = "s3")
class S3StorageConfig {

	@Bean
	StorageUrlFactory s3StorageUrlFactory(S3Properties props) {
		return new S3StorageUrlFactory(props.getBucket(), props.getRegion(), props.getPrefix(),
			props.getPublicBaseUrl());
	}

	@Bean
	S3Presigner s3Presigner(S3Properties props) {
		return S3Presigner.builder().region(Region.of(props.getRegion())).build();
	}

	@Bean
	MediaUploadSigner mediaUploadSigner(S3Presigner presigner, S3Properties props) {
		return new S3UploadSigner(presigner, props.getBucket(), props.getPrefix());
	}

	@Bean
	software.amazon.awssdk.services.s3.S3Client s3Client(S3Properties props) {
		return software.amazon.awssdk.services.s3.S3Client.builder()
			.region(Region.of(props.getRegion()))
			.build();
	}

	/** Exclusão no S3: em modo `s3` o gateway do ImageKit NAO existe (ver ImageKitConfig). */
	@Bean
	com.socialcoisa.rede.media.application.port.out.MediaStorageGateway s3MediaStorageGateway(
			software.amazon.awssdk.services.s3.S3Client s3Client, S3Properties props) {
		return new S3MediaStorageGateway(s3Client, props.getBucket());
	}

	@ConfigurationProperties(prefix = "rede.media.s3")
	static class S3Properties {

		private String bucket = "";
		private String region = "us-east-1";
		private String prefix = "uploads/";
		private String publicBaseUrl = "";

		String getBucket() { return bucket; }
		void setBucket(String bucket) { this.bucket = bucket; }
		String getRegion() { return region; }
		void setRegion(String region) { this.region = region; }
		String getPrefix() { return prefix; }
		void setPrefix(String prefix) { this.prefix = prefix; }
		String getPublicBaseUrl() { return publicBaseUrl; }
		void setPublicBaseUrl(String publicBaseUrl) { this.publicBaseUrl = publicBaseUrl; }
	}
}
