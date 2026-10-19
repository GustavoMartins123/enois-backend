package com.socialcoisa.rede.media.application.port.out;

import java.nio.file.Path;

/** Baixa o objeto original do provedor atual para um arquivo temporario (migracao). */
public interface MediaSourceDownloader {

	Path download(String sourceUrl);
}
