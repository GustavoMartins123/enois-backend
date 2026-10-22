package com.socialcoisa.rede.communities.adapters.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.socialcoisa.rede.shared.web.TestAuthHelper;

import tools.jackson.databind.ObjectMapper;

/**
 * Descrição OPCIONAL na comunidade (Etapa 7 do plano de Grupos).
 *
 * O que este teste tranca: a comunidade pode nascer sem descrição (o campo vira NULL, não um texto
 * inventado), ganhar uma depois pela edição e voltar a ficar sem — sem tocar nos registros que já
 * existiam.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CommunityDescriptionTests {

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
	void comunidadeNasceSemDescricaoEGanhaDepois() throws Exception {
		var dono = auth.authenticate("comm-desc-dono");

		MvcResult criada = mockMvc.perform(post("/communities")
				.header("X-Session-Token", dono.token())
				.header("Idempotency-Key", "comm-desc-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("name", "Comunidade Sem Descricao"))))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.description").doesNotExist())
			.andReturn();
		String id = objectMapper.readTree(criada.getResponse().getContentAsString()).get("id").asText();

		// Ganha descrição depois.
		mockMvc.perform(patch("/communities/" + id)
				.header("X-Session-Token", dono.token())
				.header("Idempotency-Key", "comm-desc-2")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("description", "Agora tem descricao"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.description").value("Agora tem descricao"));

		// E pode voltar a ficar sem (string vazia limpa).
		mockMvc.perform(patch("/communities/" + id)
				.header("X-Session-Token", dono.token())
				.header("Idempotency-Key", "comm-desc-3")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("description", ""))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.description").value(""));
	}
}
