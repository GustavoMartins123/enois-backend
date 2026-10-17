package com.socialcoisa.rede.media.adapters.out.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.socialcoisa.rede.media.application.port.out.MediaUploadSigner;
import com.socialcoisa.rede.media.application.port.out.StorageUrlFactory;

/**
 * Com `REDE_MEDIA_STORAGE=s3` o app tem de SUBIR e passar a mandar midia nova para o S3.
 *
 * Este teste existe porque faltava, e a falta custou caro: em `s3` o contexto morria com
 * "A bean with that name has already been defined" (ImageKit e S3 declaravam `storageUrlFactory`),
 * entao o pod nao subia em producao e o cluster seguia servindo a versao antiga em silencio.
 *
 * O ImageKit continua no projeto (acervo antigo e o caminho de apagar arquivo): o que muda em `s3`
 * e onde nasce a midia NOVA — o CDN antigo fica gravado na propria linha da midia.
 */
@SpringBootTest(properties = "rede.media.storage=s3")
class S3StorageModeTests {

	@Autowired
	private StorageUrlFactory storageUrlFactory;

	@Autowired
	private MediaUploadSigner mediaUploadSigner;

	@Test
	void contextoDoSobeEUsaAFabricaDoS3ComAssinaturaDeUpload() {
		assertThat(storageUrlFactory).isInstanceOf(S3StorageUrlFactory.class);
		assertThat(mediaUploadSigner).isInstanceOf(S3UploadSigner.class);
	}
}
