package com.socialcoisa.rede.media.adapters.out.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Endereços do S3.
 *
 * Sem bucket configurado o app não pode subir achando que vai funcionar: melhor falhar na hora de
 * construir o storage do que gravar mídia apontando para lugar nenhum.
 */
class S3StorageUrlFactoryTests {

	@Test
	void usesTheBucketAddressWhenThereIsNoCdn() {
		var factory = new S3StorageUrlFactory("enois-media", "sa-east-1", "uploads/", "");

		String key = factory.objectKey("media_123", "foto de perfil.png");

		assertThat(key).isEqualTo("uploads/media_123/foto%20de%20perfil.png");
		assertThat(factory.cdnUrl(key))
			.isEqualTo("https://enois-media.s3.sa-east-1.amazonaws.com/uploads/media_123/foto%20de%20perfil.png");
	}

	@Test
	void usesTheCdnBaseWhenConfigured() {
		var factory = new S3StorageUrlFactory("enois-media", "us-east-1", "midia/",
			"https://cdn.enois.ia.br");

		assertThat(factory.cdnUrl(factory.objectKey("media_9", "clipe.mp4")))
			.isEqualTo("https://cdn.enois.ia.br/midia/media_9/clipe.mp4");
	}

	@Test
	void normalizesPrefixAndDefaults() {
		// Sem prefixo: mesma forma dos objetos do ImageKit (facilita a migracao depois).
		assertThat(new S3StorageUrlFactory("b", "", null, null).objectKey("m", "a.png"))
			.isEqualTo("uploads/m/a.png");
		// Prefixo "x" vira "x/" e entra na chave (cdnUrl recebe a chave pronta, nao o prefixo).
		var comPrefixo = new S3StorageUrlFactory("b", "sa-east-1", "x", null);
		assertThat(comPrefixo.cdnUrl(comPrefixo.objectKey("m", "a.png")))
			.isEqualTo("https://b.s3.sa-east-1.amazonaws.com/x/m/a.png");
		assertThat(new S3StorageUrlFactory("b", "us-east-1", "/", null).objectKey("m", "a.png"))
			.isEqualTo("m/a.png");
	}

	@Test
	void refusesToBuildWithoutBucket() {
		assertThatThrownBy(() -> new S3StorageUrlFactory("  ", "us-east-1", "uploads/", null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("bucket");
	}

	/** O upload com S3 é por URL assinada gerada à parte; aqui o endereço é o do objeto. */
	@Test
	void uploadUrlIsTheObjectAddress() {
		var factory = new S3StorageUrlFactory("b", "us-east-1", "uploads/", "https://cdn.enois.ia.br/");

		assertThat(factory.uploadUrl("uploads/m/a.png")).isEqualTo("https://cdn.enois.ia.br/uploads/m/a.png");
	}
}
