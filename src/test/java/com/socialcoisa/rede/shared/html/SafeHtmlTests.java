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
		assertThatThrownBy(() -> SafeHtml.sanitizePageHtml("<p>" + "a".repeat(200_001) + "</p>"))
			.isInstanceOf(ApiException.class);
	}

	@Test
	void rejectsSanitizedResultAboveColumnLimit() {
		assertThatThrownBy(() -> SafeHtml.sanitizePageHtml("<p>" + "a".repeat(12_500) + "</p>"))
			.isInstanceOf(ApiException.class)
			.hasMessageContaining("12000");
	}
}
