package com.socialcoisa.rede.identity.adapters.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class TurnstileValidatorTests {

	private static final String TEST_SECRET = "1x0000000000000000000000000000000AA";
	private static final String TEST_SITE = "1x00000000000000000000AA";
	private static final String REAL_SECRET = "0x4AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
	private static final String REAL_SITE = "0x4AAAAAAAAAAAAAAA";

	private static TurnstileValidator validator(String secret, String site, boolean rejectTestKeys) {
		return new TurnstileValidator(secret, site, rejectTestKeys, JsonMapper.builder().build());
	}

	@Test
	void cloudflareTestKeysAreRefusedAtBoot() {
		assertThatThrownBy(() -> validator(TEST_SECRET, TEST_SITE, true))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("test keys");
	}

	/**
	 * A QA roda com as chaves de teste publicas da Cloudflare. La a guarda de boot esta desligada de
	 * proposito — derrubar o site por causa de captcha de teste tirou a QA do ar em 2026-10-26 —,
	 * mas isso nao pode virar "captcha ativo": chave de teste aceita qualquer token no siteverify.
	 */
	@Test
	void testKeysNeverCountAsConfiguredEvenWhenTheBootGuardIsOff() {
		TurnstileValidator qaStyle = validator(TEST_SECRET, TEST_SITE, false);

		assertThat(qaStyle.isConfigured()).isFalse();
		assertThat(qaStyle.isAvailable()).isFalse();
		// Inerte por desenho: quem passa a decidir o cadastro e o desafio proprio do backend.
		assertThatCode(() -> qaStyle.require("")).doesNotThrowAnyException();
	}

	@Test
	void realKeysStayConfiguredAndAvailable() {
		TurnstileValidator prod = validator(REAL_SECRET, REAL_SITE, true);

		assertThat(prod.isConfigured()).isTrue();
		assertThat(prod.isAvailable()).isTrue();
	}

	@Test
	void emptyKeysAreNotConfigured() {
		TurnstileValidator dev = validator("", "", true);

		assertThat(dev.isConfigured()).isFalse();
		assertThat(dev.isAvailable()).isFalse();
	}
}
