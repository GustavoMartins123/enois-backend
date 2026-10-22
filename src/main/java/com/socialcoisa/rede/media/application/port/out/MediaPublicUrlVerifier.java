package com.socialcoisa.rede.media.application.port.out;

/** Confere que o endereco publico do S3 responde antes de o cdn_url do app apontar para ele. */
public interface MediaPublicUrlVerifier {

	boolean verify(String url, String mimeType);
}
