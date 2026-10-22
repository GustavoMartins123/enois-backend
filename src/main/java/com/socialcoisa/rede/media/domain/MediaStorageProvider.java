package com.socialcoisa.rede.media.domain;

/** Onde o `cdn_url` atual aponta. A migracao copia para S3 e so troca o ponteiro quando a leitura
 * publica responde; ate la, o asset continua IMAGEKIT e o objeto antigo fica preservado. */
public enum MediaStorageProvider {
	IMAGEKIT,
	S3
}
