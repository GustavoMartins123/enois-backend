package com.socialcoisa.rede.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class RequestPathTests {

	@Test
	void retiraContextoEDecodificaLiteralUmaVez() {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/%61dmin/support");
		request.setContextPath("/api");
		assertThat(RequestPath.applicationPath(request)).isEqualTo("/admin/support");
	}

	@Test
	void naoInterpretaDuplaCodificacaoComoOutroDecode() {
		assertThat(RequestPath.applicationPath(new MockHttpServletRequest("GET", "/%2561dmin/support")))
			.isEqualTo("/%61dmin/support");
	}

	@Test
	void preservaMaisENormalizaSeparadores() {
		assertThat(RequestPath.applicationPath(new MockHttpServletRequest("GET", "//users/a+b")))
			.isEqualTo("/users/a+b");
	}

	@Test
	void codificacaoMalformadaNaoLancaExcecaoNoGuard() {
		assertThat(RequestPath.applicationPath(new MockHttpServletRequest("GET", "/%zz")))
			.isEqualTo("/%zz");
	}

	@Test
	void leituraDoRoteamentoPreservaBarrasDuplas() {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth//%63onfig");
		assertThat(RequestPath.routingPath(request)).isEqualTo("/auth//config");
		assertThat(RequestPath.applicationPath(request)).isEqualTo("/auth/config");
		assertThat(request.getRequestURI()).isEqualTo("/auth//%63onfig");
	}

	@Test
	void naoReescreveUriQueryOuCorpoAoLerOCaminho() {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/users/a%2Bb");
		request.setQueryString("next=/%61dmin/support&label=a+b");
		byte[] body = "conteudo com %61dmin e +".getBytes(java.nio.charset.StandardCharsets.UTF_8);
		request.setContent(body);
		assertThat(RequestPath.applicationPath(request)).isEqualTo("/users/a+b");
		assertThat(request.getRequestURI()).isEqualTo("/users/a%2Bb");
		assertThat(request.getQueryString()).isEqualTo("next=/%61dmin/support&label=a+b");
		assertThat(request.getContentAsByteArray()).containsExactly(body);
	}

	@Test
	void preservaUnicodeCaixaESemicolonCodificadoComoDados() {
		assertThat(RequestPath.routingPath(new MockHttpServletRequest("GET", "/users/a%C3%A7%C3%A3o")))
			.isEqualTo("/users/ação");
		assertThat(RequestPath.routingPath(new MockHttpServletRequest("GET", "/Admin/support")))
			.isEqualTo("/Admin/support");
		assertThat(RequestPath.routingPath(new MockHttpServletRequest("GET", "/users/a%3Bb")))
			.isEqualTo("/users/a;b");
	}
}
