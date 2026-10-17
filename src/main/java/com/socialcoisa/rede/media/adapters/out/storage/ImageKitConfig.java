package com.socialcoisa.rede.media.adapters.out.storage;

import java.util.Base64;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import com.socialcoisa.rede.media.application.port.out.MediaStorageGateway;
import com.socialcoisa.rede.media.application.port.out.StorageUrlFactory;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ImageKitConfig.ImageKitProperties.class)
// O gateway do ImageKit segue incondicional: e ele que recebe e apaga arquivo hoje, e o S3 ainda
// nao cobre esse caminho. Ja a FABRICA DE URL e alternativa ao S3 — as duas juntas colidiam no
// mesmo nome de bean (`storageUrlFactory`) e o boot morria com "bean already defined" quando
// `REDE_MEDIA_STORAGE=s3`. Agora cada uma so existe no seu modo; sem a variavel, o padrao e ImageKit.
class ImageKitConfig {

	@Bean
	@ConditionalOnProperty(name = "rede.media.storage", havingValue = "imagekit", matchIfMissing = true)
	StorageUrlFactory storageUrlFactory(ImageKitProperties props) {
		return new ImageKitStorageUrlFactory(props.getUrlEndpoint());
	}

	@Bean
	MediaStorageGateway mediaStorageGateway(ImageKitProperties props) {
		String auth = Base64.getEncoder().encodeToString((props.getPrivateKey() + ":").getBytes());
		RestClient client = RestClient.builder()
			.baseUrl("https://api.imagekit.io/v1")
			.defaultHeader("Authorization", "Basic " + auth)
			.build();
		return new ImageKitMediaStorageGateway(client);
	}

	@ConfigurationProperties(prefix = "rede.media.imagekit")
	static class ImageKitProperties {

		private String urlEndpoint = "";
		private String publicKey = "";
		private String privateKey = "";

		String getUrlEndpoint() { return urlEndpoint; }
		void setUrlEndpoint(String urlEndpoint) { this.urlEndpoint = urlEndpoint; }
		String getPublicKey() { return publicKey; }
		void setPublicKey(String publicKey) { this.publicKey = publicKey; }
		String getPrivateKey() { return privateKey; }
		void setPrivateKey(String privateKey) { this.privateKey = privateKey; }
	}
}
