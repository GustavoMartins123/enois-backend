package com.socialcoisa.rede.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PiiMaskTests {

	@Test
	void mascaraMantendoDominioEInicial() {
		assertThat(PiiMask.email("fabitmap@gmail.com")).isEqualTo("f***@gmail.com");
		assertThat(PiiMask.email("a@b.com")).isEqualTo("***@b.com");
		assertThat(PiiMask.email(" sem-arroba ")).isEqualTo("***");
		assertThat(PiiMask.email(null)).isNull();
	}
}
