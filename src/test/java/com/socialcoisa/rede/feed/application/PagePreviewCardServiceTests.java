package com.socialcoisa.rede.feed.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * Cartao de compartilhamento: desenha o TEXTO da pagina numa imagem 1200x630.
 *
 * Teste de unidade porque o desenho (Java2D) e o ponto fragil: fonte empacotada ausente ou ambiente
 * sem interface grafica quebram a geracao, e isso nao aparece em teste de contrato.
 */
class PagePreviewCardServiceTests {

	private final PagePreviewCardService service = new PagePreviewCardService();

	@Test
	void rendersThePageTextIntoAPng() {
		byte[] png = service.render("post_exemplo",
			"<style>.x{color:red}</style><h1>Meu portfolio de dados</h1>"
				+ "<p>Sou analista e escrevo sobre pipelines de dados, series e visualizacao.</p>",
			"Melissa Monfre");

		assertThat(png.length).isGreaterThan(5_000);
		assertThat(png[0]).isEqualTo((byte) 0x89);
		assertThat(new String(png, 1, 3, StandardCharsets.US_ASCII)).isEqualTo("PNG");
	}

	@Test
	void rendersEvenWithNoTextAtAll() {
		byte[] png = service.render("post_vazio", "", null);

		assertThat(png[0]).isEqualTo((byte) 0x89);
	}
}
