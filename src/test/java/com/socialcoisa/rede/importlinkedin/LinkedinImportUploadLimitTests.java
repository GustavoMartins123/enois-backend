package com.socialcoisa.rede.importlinkedin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * O ZIP do LinkedIn tem alguns megabytes (o export real do Basic fica em ~2,5 MB), e o padrão do
 * Servlet no Spring Boot é recusar arquivo acima de **1 MB** — o 500 "Erro interno inesperado" que
 * apareceu para a usuária vinha daí: o arquivo nem chegava ao controller.
 *
 * O teste roda num Tomcat de verdade (`RANDOM_PORT`): o MockMvc ignora os limites de multipart do
 * Servlet, então só um servidor real prova a configuração.
 *
 * <p><b>Por que um socket cru no segundo teste:</b> o servidor recusa o upload no MEIO da stream
 * (é assim que o multipart funciona) e fecha a conexão — o cliente ainda tem bytes para escrever
 * e toma "pipe quebrado" antes de conseguir ler a resposta. Com um cliente HTTP comum, o teste era
 * uma corrida: passava em máquina folgada, falhava com a suíte inteira rodando (foi assim que o
 * `main` publicado ficou vermelho — 718 testes, 1 erro, sem nada mudado). Aqui o corpo do upload
 * vai numa thread separada e a resposta é lida na outra: o teste vê o 413 com `FILE_TOO_LARGE`
 * sempre, que é o que importa.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LinkedinImportUploadLimitTests {

	/** A fronteira do multipart: qualquer string serve, desde que não apareça no arquivo. */
	private static final String FRONTEIRA = "----enois-limite-multipart";

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
		// Passa do limite configurado (8 MB no perfil de teste) por pouco, de propósito: o resto
		// pequeno é o que o servidor engole antes de fechar a conexão.
		byte[] zip = zipOf(8_450_000);
		assertThat(zip.length).as("precisa passar o limite configurado").isGreaterThan(8 * 1024 * 1024);

		var response = uploadLendoARespostaDuranteOEnvio(zip);

		assertThat(response.status()).isEqualTo(413);
		assertThat(response.body()).contains("FILE_TOO_LARGE");
	}

	private record Result(int status, String body) {
	}

	/**
	 * Envia o arquivo inteiro e espera a resposta. Só serve para o arquivo que cabe no limite.
	 */
	private Result upload(byte[] zip) throws Exception {
		try (Socket socket = new Socket("localhost", port)) {
			socket.setSoTimeout(30_000);
			OutputStream out = socket.getOutputStream();
			out.write(cabecalho(zip.length));
			out.write(corpoDoUpload(zip));
			out.flush();
			return lerResposta(socket.getInputStream());
		}
	}

	/**
	 * Envia o arquivo e lê a resposta <b>enquanto</b> envia. O servidor pode responder 413 e fechar
	 * a conexão no meio do envio: a thread de escrita vai tomar o "pipe quebrado" (e tudo bem), e a
	 * thread que lê pega a resposta antes de a conexão sumir.
	 */
	private Result uploadLendoARespostaDuranteOEnvio(byte[] zip) throws Exception {
		try (Socket socket = new Socket("localhost", port)) {
			socket.setSoTimeout(30_000);
			OutputStream out = socket.getOutputStream();
			out.write(cabecalho(zip.length));
			out.flush();

			Thread escrita = new Thread(() -> {
				try {
					out.write(corpoDoUpload(zip));
					out.flush();
				}
				catch (Exception ignored) {
					// O servidor fechou a conexao no meio do envio: e o que este teste espera.
				}
			});
			escrita.setDaemon(true);
			escrita.start();

			Result result = lerResposta(socket.getInputStream());
			escrita.join(5_000);
			return result;
		}
	}

	/** Cabeçalho HTTP/1.1 do POST multipart, com o tamanho exato do corpo. */
	private byte[] cabecalho(int tamanhoDoArquivo) {
		int total = tamanhoDoArquivo + bordasDoUpload().length;
		return ("POST /me/import/linkedin HTTP/1.1\r\n"
			+ "Host: localhost:" + port + "\r\n"
			+ "Content-Type: multipart/form-data; boundary=" + FRONTEIRA + "\r\n"
			+ "Content-Length: " + total + "\r\n"
			+ "Connection: close\r\n"
			+ "\r\n").getBytes(StandardCharsets.UTF_8);
	}

	/** As bordas do multipart (antes e depois do arquivo), sem o conteúdo do arquivo. */
	private byte[] bordasDoUpload() {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try {
			bytes.write((("--" + FRONTEIRA + "\r\n"
				+ "Content-Disposition: form-data; name=\"file\"; filename=\"linkedin.zip\"\r\n"
				+ "Content-Type: application/zip\r\n\r\n").getBytes(StandardCharsets.UTF_8)));
			bytes.write(("\r\n--" + FRONTEIRA + "--\r\n").getBytes(StandardCharsets.UTF_8));
		}
		catch (Exception exception) {
			throw new IllegalStateException(exception);
		}
		return bytes.toByteArray();
	}

	/** O multipart: a parte com o arquivo, entre as marcas da fronteira. */
	private byte[] corpoDoUpload(byte[] zip) {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try {
			bytes.write((("--" + FRONTEIRA + "\r\n"
				+ "Content-Disposition: form-data; name=\"file\"; filename=\"linkedin.zip\"\r\n"
				+ "Content-Type: application/zip\r\n\r\n").getBytes(StandardCharsets.UTF_8)));
			bytes.write(zip);
			bytes.write(("\r\n--" + FRONTEIRA + "--\r\n").getBytes(StandardCharsets.UTF_8));
		}
		catch (Exception exception) {
			throw new IllegalStateException(exception);
		}
		return bytes.toByteArray();
	}

	/** Lê a resposta HTTP: a linha de status e o corpo. */
	private Result lerResposta(InputStream in) throws Exception {
		String status = "";
		int tamanhoDoCorpo = -1;
		ByteArrayOutputStream corpo = new ByteArrayOutputStream();
		String linha = lerLinha(in);
		status = linha.split(" ")[1];
		while ((linha = lerLinha(in)) != null && !linha.isEmpty()) {
			if (linha.toLowerCase().startsWith("content-length:")) {
				tamanhoDoCorpo = Integer.parseInt(linha.substring(linha.indexOf(':') + 1).trim());
			}
		}
		if (tamanhoDoCorpo >= 0) {
			byte[] buffer = new byte[tamanhoDoCorpo];
			int lidos = 0;
			while (lidos < tamanhoDoCorpo) {
				int n = in.read(buffer, lidos, tamanhoDoCorpo - lidos);
				if (n < 0) {
					break;
				}
				lidos += n;
			}
			corpo.write(buffer, 0, lidos);
		}
		else {
			corpo.write(in.readAllBytes());
		}
		return new Result(Integer.parseInt(status), corpo.toString(StandardCharsets.UTF_8));
	}

	/** Uma linha do stream (sem o \\n final). */
	private String lerLinha(InputStream in) throws Exception {
		ByteArrayOutputStream linha = new ByteArrayOutputStream();
		int b;
		while ((b = in.read()) != -1) {
			if (b == '\n') {
				break;
			}
			if (b != '\r') {
				linha.write(b);
			}
		}
		if (b == -1 && linha.size() == 0) {
			return null;
		}
		return linha.toString(StandardCharsets.UTF_8);
	}

	/**
	 * ZIP com um CSV grande o bastante, de tamanho <b>exato</b>.
	 *
	 * <p>O conteúdo é ALEATÓRIO de propósito (texto repetido comprime demais), e a entrada vai
	 * <b>armazenada</b> (STORED, sem compressão): assim o ZIP sai com o tamanho pedido, e não com um
	 * tamanho que depende de quanto o deflate apertou — o teste precisa saber que o arquivo passou
	 * do limite de 8 MB.
	 */
	private byte[] zipOf(int targetBytes) throws Exception {
		StringBuilder csv = new StringBuilder("Position Title,Company Name\n");
		Random random = new Random(7);
		while (csv.length() < targetBytes) {
			csv.append('"').append(randomText(random, 40)).append("\",\"")
				.append(randomText(random, 40)).append("\"\n");
		}
		byte[] data = csv.toString().getBytes(StandardCharsets.UTF_8);
		CRC32 crc = new CRC32();
		crc.update(data);
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			ZipEntry entry = new ZipEntry("Positions.csv");
			entry.setMethod(ZipEntry.STORED);
			entry.setSize(data.length);
			entry.setCompressedSize(data.length);
			entry.setCrc(crc.getValue());
			zip.putNextEntry(entry);
			zip.write(data);
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
