package com.socialcoisa.rede.apiv1.origins;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

/**
 * Regressao: o construtor juntava as origens do app com as confiaveis usando `addAll` num conjunto
 * IMUTAVEL. Isso lancava `UnsupportedOperationException` na criacao do bean e derrubava o boot do
 * app inteiro (aconteceu na QA em 2026-10-23).
 */
class OriginAbuseGuardTests {

	@Test
	void constroiComAsDuasListasDeOrigem() {
		assertThatCode(() -> new OriginAbuseGuard(null,
			"https://enois.ia.br,http://localhost:5173", "https://*.b7cloud.com.br", 60, 300, 30))
			.doesNotThrowAnyException();
	}

	@Test
	void constroiComListaConfiavelVazia() {
		assertThatCode(() -> new OriginAbuseGuard(null, "https://enois.ia.br", "", 60, 300, 30))
			.doesNotThrowAnyException();
	}
}
