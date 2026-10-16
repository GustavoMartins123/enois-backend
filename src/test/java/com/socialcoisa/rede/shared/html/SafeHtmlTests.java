package com.socialcoisa.rede.shared.html;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Limpeza do HTML das paginas. Estes testes existem porque o sanitizador e a unica barreira que
 * segue valendo fora do navegador (app, e-mail, crawler) — o iframe sandboxed protege o front,
 * mas nao protege quem consumir o conteudo por outro caminho.
 */
class SafeHtmlTests {

	@Test
	void removesScriptTagAndItsContent() {
		String clean = SafeHtml.sanitizePageHtml("<p>antes</p><script>alert('xss')</script><p>depois</p>");

		assertThat(clean).doesNotContain("<script").doesNotContain("alert");
		assertThat(clean).contains("antes").contains("depois");
	}

	@Test
	void removesEventHandlersEvenOnAllowedTags() {
		String clean = SafeHtml.sanitizePageHtml("<img src=\"https://exemplo.com/foto.png\" onerror=\"alert(1)\">");

		assertThat(clean).doesNotContain("onerror").doesNotContain("alert");
		assertThat(clean).contains("https://exemplo.com/foto.png");
	}

	@Test
	void removesJavascriptUrls() {
		String clean = SafeHtml.sanitizePageHtml("<a href=\"javascript:alert(1)\">clique</a>");

		assertThat(clean).doesNotContain("javascript:");
		assertThat(clean).contains("clique");
	}

	@Test
	void keepsFormattingTablesListsAndLinks() {
		String clean = SafeHtml.sanitizePageHtml("""
			<h2>Titulo</h2><p><strong>negrito</strong> e <em>italico</em></p>
			<ul><li>um</li><li>dois</li></ul>
			<table><tr><th>a</th><th>b</th></tr><tr><td>1</td><td>2</td></tr></table>
			<a href="https://exemplo.com">link</a>
			""");

		assertThat(clean).contains("<h2>Titulo</h2>");
		assertThat(clean).contains("<strong>negrito</strong>");
		assertThat(clean).contains("<li>um</li>");
		assertThat(clean).contains("<th>a</th>");
		assertThat(clean).contains("href=\"https://exemplo.com\"");
	}

	/**
	 * Midia embutida: imagem, video e audio rodam no navegador sem script nenhum (quem toca e o
	 * proprio navegador, com controles nativos), entao a promessa de "nenhum script executa"
	 * continua valendo com elas.
	 */
	@Test
	void keepsNativeMediaSoItPlaysInTheBrowser() {
		String clean = SafeHtml.sanitizePageHtml("""
			<figure><img src="https://exemplo.com/foto.png" alt="foto"><figcaption>A foto</figcaption></figure>
			<video controls poster="https://exemplo.com/capa.jpg" src="https://exemplo.com/clipe.mp4">texto</video>
			<audio controls src="https://exemplo.com/musica.mp3"></audio>
			<picture><source src="https://exemplo.com/foto.webp" type="image/webp"><img src="https://exemplo.com/foto.png" alt=""></picture>
			<video controls><source src="https://exemplo.com/b.webm" type="video/webm"><track src="https://exemplo.com/leg.vtt" kind="captions"></video>
			""");

		assertThat(clean).contains("<figcaption>A foto</figcaption>");
		assertThat(clean).contains("controls");
		assertThat(clean).contains("poster=\"https://exemplo.com/capa.jpg\"");
		assertThat(clean).contains("<audio");
		assertThat(clean).contains("<source src=\"https://exemplo.com/b.webm\"");
		assertThat(clean).contains("<track src=\"https://exemplo.com/leg.vtt\"");
		assertThat(clean).doesNotContain("autoplay");
	}

	@Test
	void mediaOnlyByHttpsAndDocumentsStayAsLinks() {
		// http nao carregaria numa pagina https (conteudo misto): o atributo sai.
		String httpMedia = SafeHtml.sanitizePageHtml("<video controls src=\"http://exemplo.com/clipe.mp4\"></video>");
		assertThat(httpMedia).doesNotContain("http://");

		// Quadro dentro de quadro exigiria liberar script (player de terceiros) e o visualizador de
		// PDF do Chrome tambem nao roda no sandbox: documento vai como link, que abre no navegador.
		String embeds = SafeHtml.sanitizePageHtml(
			"<iframe src=\"https://exemplo.com/doc.pdf\"></iframe><embed src=\"https://exemplo.com/doc.pdf\">"
				+ "<object data=\"https://exemplo.com/doc.pdf\"></object><a href=\"https://exemplo.com/doc.pdf\">Abrir</a>");

		assertThat(embeds).doesNotContain("<iframe").doesNotContain("<embed").doesNotContain("<object");
		assertThat(embeds).contains("href=\"https://exemplo.com/doc.pdf\"");
	}

	@Test
	void keepsSafeInlineStyleAndDropsRemoteCss() {
		String clean = SafeHtml.sanitizePageHtml(
			"<div style=\"color:#123456;background:url(https://exemplo.com/bg.png);border-image:url(http://rastreador.test/pixel)\">conteudo</div>");

		assertThat(clean).contains("color").contains("#123456");
		assertThat(clean).contains("https://exemplo.com/bg.png");
		assertThat(clean).doesNotContain("rastreador.test");
	}

	@Test
	void styleBlockIsPreservedButSanitized() {
		String clean = SafeHtml.sanitizePageHtml("""
			<html><head><style>
			@import url("https://externo.test/estilo.css");
			.card { color: red; background: url("http://externo.test/a.png"); padding: 8px }
			.x { width: expression(alert(1)) }
			</style></head><body><div class="card">oi</div></body></html>
			""");

		assertThat(clean).contains("<style>");
		assertThat(clean).contains(".card");
		assertThat(clean).doesNotContain("@import").doesNotContain("expression").doesNotContain("externo.test");
		assertThat(clean).contains("<div class=\"card\">oi</div>");
	}

	@Test
	void cannotBreakOutOfStyleBlock() {
		String clean = SafeHtml.sanitizePageHtml("<style>body{color:red}</style><!--</style><script>alert(1)</script>--><p>ok</p>");

		assertThat(clean).doesNotContain("<script");
		assertThat(clean).doesNotContain("alert");
		assertThat(clean).contains("ok");
	}

	@Test
	void blankInputBecomesNullAndPageTypeRequiresHtml() {
		assertThat(SafeHtml.sanitizePageHtml("   ")).isNull();
		assertThat(SafeHtml.sanitizePageHtml(null)).isNull();
		// Sobra so markup perigoso: nada util para renderizar, entao a publicacao tem de falhar.
		assertThat(SafeHtml.sanitizePageHtml("<script>alert(1)</script>")).isNull();
		assertThatThrownBy(() -> SafeHtml.requirePageHtml("<script>alert(1)</script>"))
			.isInstanceOf(ApiException.class)
			.hasMessageContaining("HTML");
	}

	@Test
	void rejectsHugePastesWithoutParsing() {
		assertThatThrownBy(() -> SafeHtml.sanitizePageHtml("<p>" + "a".repeat(400_001) + "</p>"))
			.isInstanceOf(ApiException.class);
	}

	@Test
	void acceptsPageNearTheLimitAndRejectsOnlyAboveIt() {
		// O teto e um so para todo mundo: abaixo dele a pagina passa, acima e recusada — sem
		// faixa intermediaria, sem excecao por conta.
		assertThat(SafeHtml.sanitizePageHtml("<p>" + "a".repeat(SafeHtml.MAX_PAGE_HTML - 10) + "</p>"))
			.isNotNull();
		assertThatThrownBy(() -> SafeHtml.sanitizePageHtml("<p>" + "a".repeat(SafeHtml.MAX_PAGE_HTML) + "</p>"))
			.isInstanceOf(ApiException.class)
			.hasMessageContaining(String.valueOf(SafeHtml.MAX_PAGE_HTML));
	}
}
