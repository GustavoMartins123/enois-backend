package com.socialcoisa.rede.importlinkedin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * O ZIP do LinkedIn tem alguns megabytes (o export real do Basic fica em ~2,5 MB), e o padrão do
 * Servlet no Spring Boot é recusar arquivo acima de **1 MB** — o 500 "Erro interno inesperado" que
 * apareceu para a usuária vinha daí: o arquivo nem chegava ao controller.
 *
 * O teste roda num Tomcat de verdade (`RANDOM_PORT`): o MockMvc ignora os limites de multipart do
 * Servlet, então só um servidor real prova a configuração.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LinkedinImportUploadLimitTests {

	@LocalServerPort
	private int port;

	@Test
	void aTwoMegabyteZipReachesTheControllerInsteadOfFailingAtTheServlet() throws Exception {
		byte[] zip = zipOf(3_000_000);
		assertThat(zip.length).as("precisa passar do padrao antigo de 1 MB").isGreaterThan(1_048_576);

		var response = upload(zip);

		// Sem sessão a resposta correta é 401 — o que importa é NÃO ser o 500 do limite de multipart.
		assertThat(response.status()).isEqualTo(401);
	}

	@Test
	void aFileAboveTheConfiguredLimitGetsAClearError() throws Exception {
		byte[] zip = zipOf(10_000_000);
		assertThat(zip.length).as("precisa passar do limite configurado").isGreaterThan(8 * 1024 * 1024);

		var response = upload(zip);

		assertThat(response.status()).isEqualTo(413);
		assertThat(response.body()).contains("FILE_TOO_LARGE");
	}

	private record Result(int status, String body) {
	}

	private Result upload(byte[] zip) {
		var parts = new LinkedMultiValueMap<String, Object>();
		parts.add("file", new ByteArrayResource(zip) {
			@Override
			public String getFilename() {
				return "linkedin.zip";
			}
		});
		return RestClient.create().post()
			.uri("http://localhost:" + port + "/me/import/linkedin")
			.contentType(MediaType.MULTIPART_FORM_DATA)
			.body(parts)
			.exchange((request, response) -> new Result(response.getStatusCode().value(),
				new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)));
	}

	/**
	 * ZIP com um CSV grande o bastante. O conteúdo é ALEATÓRIO de propósito: texto repetido comprime
	 * demais e um ZIP de 10 MB de entrada viraria 40 KB na saída, sem exercitar o limite.
	 */
	private byte[] zipOf(int targetBytes) throws Exception {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			zip.putNextEntry(new ZipEntry("Positions.csv"));
			StringBuilder csv = new StringBuilder("Position Title,Company Name\n");
			Random random = new Random(7);
			while (csv.length() < targetBytes) {
				csv.append('"').append(randomText(random, 40)).append("\",\"")
					.append(randomText(random, 40)).append("\"\n");
			}
			zip.write(csv.toString().getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
		}
		return bytes.toByteArray();
	}

	private String randomText(Random random, int length) {
		String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
		StringBuilder text = new StringBuilder(length);
		for (int i = 0; i < length; i++) {
			text.append(alphabet.charAt(random.nextInt(alphabet.length())));
		}
		return text.toString();
	}
}
