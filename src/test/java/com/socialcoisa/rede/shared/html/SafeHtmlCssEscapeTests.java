package com.socialcoisa.rede.shared.html;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Os escapes de CSS sao decodificados para os filtros enxergarem a palavra-chave, mas a saida tem
 * que ser lida pelo navegador do mesmo jeito que o original: nem escape novo formado pela barra
 * decodificada, nem string do autor quebrada por aspas ou quebra de linha decodificadas.
 */
class SafeHtmlCssEscapeTests {

	@Test
	void anEscapedBackslashDoesNotBecomeANewEscapeForUrl() {
		String style = SafeHtml.sanitizePageHtml(
			"<style>p{background:\\5c 75rl(https://audit.invalid/pixel)}</style><p>ok</p>");
		String inline = SafeHtml.sanitizePageHtml(
			"<p style=\"background:\\5c 75rl(https://audit.invalid/pixel)\">ok</p>");

		// `\75rl(` seria lido como `url(`; `\5c 75rl(` e so uma funcao com nome literal.
		assertThat(style).doesNotContain(":\\75rl(");
		assertThat(inline).doesNotContain(":\\75rl(");
	}

	@Test
	void decodedQuotesAndNewlinesKeepTheAuthorsStringsValid() {
		String lineBreak = SafeHtml.sanitizePageHtml("<style>.b::before{content:\"\\201C\\A\"}</style><p>ok</p>");
		String quote = SafeHtml.sanitizePageHtml("<style>.q::before{content:\"\\\"\"}</style><p>ok</p>");

		assertThat(lineBreak).doesNotContain("\n").contains("content:\"“\\a \"");
		assertThat(quote).contains("content:\"\\22 \"");
	}

	@Test
	void sanitizingAgainOnReadKeepsTheSameCss() {
		for (String raw : List.of(
				"<style>.b::before{content:\"\\201C\\A\"}</style><p>ok</p>",
				"<style>.q::before{content:\"\\\"\"}</style><p>ok</p>",
				"<style>p{background:\\5c 75rl(https://audit.invalid/pixel)}</style><p>ok</p>")) {
			String stored = SafeHtml.sanitizePageHtml(raw);
			assertThat(SafeHtml.sanitizePageHtml(stored)).as(raw).isEqualTo(stored);
		}
	}
}
