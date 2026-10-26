package com.socialcoisa.rede.identity.adapters.out;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class TurnstileValidatorTests {

	@Test
	void cloudflareTestKeysAreRefusedAtBoot() {
		assertThatThrownBy(() -> new TurnstileValidator("1x0000000000000000000000000000000AA",
				"1x00000000000000000000AA", true, JsonMapper.builder().build()))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("test keys");
	}
}
