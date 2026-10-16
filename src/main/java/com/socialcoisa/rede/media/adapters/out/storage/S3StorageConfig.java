package com.socialcoisa.rede.media.adapters.out.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.socialcoisa.rede.media.application.port.out.StorageUrlFactory;

/**
 * Storage S3, ligado por `REDE_MEDIA_STORAGE=s3`.
 *
 * O padrão continua sendo o ImageKit: virar o storage é uma decisão de operação, tomada por
 * variável de ambiente, e voltar atrás é trocar a variável de novo — nenhum código muda. Enquanto
 * estiver em `imagekit`, este bloco nem é criado.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(S3StorageConfig.S3Properties.class)
@ConditionalOnProperty(name = "rede.media.storage", havingValue = "s3")
class S3StorageConfig {

	/**
	 * Só o endereço dos objetos novos. O upload ainda não tem caminho S3 — e é de propósito que
	 * ligar o S3 hoje FALHE no boot: subir com os objetos apontando para o S3 enquanto o arquivo
	 * continua indo para o ImageKit gravaria mídia quebrada, e o erro apareceria no post do usuário,
	 * não aqui. Esta trava sai quando o upload por URL assinada entrar (próximo passo).
	 */
	@Bean
	StorageUrlFactory storageUrlFactory(S3Properties props) {
		// ignore: props validam bucket/regiao quando o caminho existir.
		throw new IllegalStateException("rede.media.storage=s3 ainda nao tem caminho de upload: "
			+ "mantenha imagekit ate o upload por URL assinada entrar.");
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
