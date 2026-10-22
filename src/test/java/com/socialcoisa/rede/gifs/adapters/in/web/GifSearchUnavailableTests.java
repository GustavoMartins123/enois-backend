package com.socialcoisa.rede.gifs.adapters.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.socialcoisa.rede.shared.web.TestAuthHelper;

import tools.jackson.databind.ObjectMapper;

/**
 * Sem chave configurada (o padrao dos testes) o seletor precisa dizer que a busca esta INDISPONIVEL —
 * `available=false` e lista vazia. Sem isso o front mostraria "nenhum GIF encontrado" para uma busca
 * que nunca aconteceu, e a pessoa ficaria procurando o defeito no lugar errado.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GifSearchUnavailableTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	private TestAuthHelper auth;

	@BeforeEach
	void setUp() {
		auth = new TestAuthHelper(mockMvc, objectMapper);
	}

	@Test
	void reportsUnavailableWhenNoProviderHasAKey() throws Exception {
		var user = auth.authenticate("user_gif_sem_chave");

		mockMvc.perform(get("/gifs/search").param("q", "qualquer-coisa").header("X-Session-Token", user.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.available").value(false))
			.andExpect(jsonPath("$.items").isEmpty());
	}
}
