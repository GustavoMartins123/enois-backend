package com.socialcoisa.rede.media.application.port.out;

import java.nio.file.Path;

/** Sobe o arquivo para o S3, confere o objeto la (tamanho) e devolve o endereco publico. */
public interface MediaS3Copier {

	String copy(String objectKey, Path source, String mimeType);
}
