package com.socialcoisa.rede.shared.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Varredura da base pelas rotas públicas de pessoas: o teto por IP derruba a enumeração em
 * segundos. Limite baixo neste contexto para o teste caber em poucas requisições.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PublicReadGuardTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired(required = false)
	private PublicReadRateLimitFilter filter;

	@DynamicPropertySource
	static void props(DynamicPropertyRegistry registry) {
		registry.add("rede.rate.public-read-per-minute", () -> "5");
	}

	@Test
	void enumerationOnThePeopleSearchIsCutOff() throws Exception {
		org.assertj.core.api.Assertions.assertThat(filter).as("filtro de leitura pública no contexto")
			.isNotNull();
		for (int i = 0; i < 5; i++) {
			mockMvc.perform(get("/users/search").param("q", "ab" + i))
				.andExpect(status().isOk());
		}
		mockMvc.perform(get("/users/search").param("q", "ab-final"))
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("ENUMERATION_GUARD"));
	}
}
