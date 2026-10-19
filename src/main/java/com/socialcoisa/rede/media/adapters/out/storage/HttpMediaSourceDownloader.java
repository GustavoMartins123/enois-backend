package com.socialcoisa.rede.media.adapters.out.storage;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import com.socialcoisa.rede.media.application.port.out.MediaSourceDownloader;

/** Baixa o original do provedor atual para um arquivo temporario; o chamador apaga. */
class HttpMediaSourceDownloader implements MediaSourceDownloader {

	/** Teto de seguranca: maior que o maior upload aceito, pequeno o bastante para nao encher o disco. */
	private static final long MAX_BYTES = 512L * 1024 * 1024;

	private final HttpClient http = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(10))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();

	@Override
	public Path download(String sourceUrl) {
		Path temp = null;
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(sourceUrl))
				.timeout(Duration.ofMinutes(3))
				.GET()
				.build();
			HttpResponse<InputStream> response = http.send(request, BodyHandlers.ofInputStream());
			if (response.statusCode() != 200) {
				response.body().close();
				throw new IllegalStateException("origem respondeu " + response.statusCode());
			}
			temp = Files.createTempFile("media-migration-", ".bin");
			long total = 0;
			try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(temp)) {
				byte[] buffer = new byte[64 * 1024];
				int read;
				while ((read = in.read(buffer)) != -1) {
					total += read;
					if (total > MAX_BYTES) {
						throw new IllegalStateException("objeto maior que o teto de migracao");
					}
					out.write(buffer, 0, read);
				}
			}
			if (total == 0) {
				throw new IllegalStateException("origem respondeu vazio");
			}
			return temp;
		}
		catch (Exception exception) {
			if (temp != null) {
				try {
					Files.deleteIfExists(temp);
				}
				catch (Exception ignored) {
				}
			}
			throw new IllegalStateException("download falhou: " + exception.getMessage(), exception);
		}
	}
}
