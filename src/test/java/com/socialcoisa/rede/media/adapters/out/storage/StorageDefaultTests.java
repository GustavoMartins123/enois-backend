package com.socialcoisa.rede.media.adapters.out.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.socialcoisa.rede.media.application.port.out.StorageUrlFactory;

/**
 * Sem a variavel de ambiente, o app tem de continuar no ImageKit.
 *
 * E o que garante que ligar o S3 e uma decisao de operacao: nenhum deploy muda o storage sozinho.
 */
@SpringBootTest
class StorageDefaultTests {

	@Autowired
	private StorageUrlFactory storageUrlFactory;

	@Test
	void staysOnImageKitByDefault() {
		assertThat(storageUrlFactory).isInstanceOf(ImageKitStorageUrlFactory.class);
	}
}
