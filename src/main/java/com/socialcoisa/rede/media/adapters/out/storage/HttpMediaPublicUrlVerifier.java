package com.socialcoisa.rede.media.adapters.out.storage;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;

import com.socialcoisa.rede.media.application.port.out.MediaPublicUrlVerifier;

/**
 * Confere que o endereco publico do S3 responde ANTES de o cdn_url do app apontar para ele. Se o
 * CDN estiver sem certificado/403 (o estado atual), a verificacao falha e nenhum asset e relinkado
 * — ninguem perde midia.
 */
class HttpMediaPublicUrlVerifier implements MediaPublicUrlVerifier {

	private final HttpClient http = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(5))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();

	@Override
	public boolean verify(String url, String mimeType) {
		try {
			HttpRequest head = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(10))
				.method("HEAD", HttpRequest.BodyPublishers.noBody())
				.build();
			HttpResponse<Void> response = http.send(head, BodyHandlers.discarding());
			if (response.statusCode() == 405) {
				// Alguns CDNs recusam HEAD; um GET de 1 byte resolve sem baixar o arquivo.
				HttpRequest get = HttpRequest.newBuilder(URI.create(url))
					.timeout(Duration.ofSeconds(10))
					.header("Range", "bytes=0-0")
					.GET()
					.build();
				response = http.send(get, BodyHandlers.discarding());
			}
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				return false;
			}
			String contentType = response.headers().firstValue("content-type").orElse("");
			if (contentType.isBlank() || mimeType == null || mimeType.isBlank()) {
				return true;
			}
			String expectedTop = mimeType.split("/")[0];
			return contentType.startsWith(expectedTop);
		}
		catch (Exception exception) {
			return false;
		}
	}
}
