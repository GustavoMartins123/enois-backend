package com.socialcoisa.rede.profile.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.socialcoisa.rede.shared.web.TestAuthHelper;

/**
 * "Vulgo" (aka) do perfil: como a pessoa e conhecida, uma etiqueta ao lado do nome.
 *
 * O que este teste protege: a leitura de volta nas DUAS projecoes (a do dono, que alimenta o
 * formulario, e a publica, que e a que aparece na tela), o "limpar" com string vazia — o servidor
 * preserva o valor quando o campo vem ausente, entao vazio e a unica forma de remover — e o teto de
 * 60 caracteres, que precisa ser recusado na validacao e nao no banco.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProfileAkaTests {

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
	void savesTheAkaAndReadsItBackInBothViews() throws Exception {
		var user = auth.authenticate("user_aka1");

		mockMvc.perform(patch("/me/profile")
				.header("X-Session-Token", user.token())
				.header("Idempotency-Key", "aka-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("aka", "Java"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.aka").value("Java"));

		// Dono: o formulario de edicao le daqui.
		MvcResult me = mockMvc.perform(get("/me").header("X-Session-Token", user.token()))
			.andExpect(status().isOk())
			.andReturn();
		JsonNode mine = objectMapper.readTree(me.getResponse().getContentAsString());
		assertThat(mine.get("aka").asText()).isEqualTo("Java");

		// Publico: e a projecao que a tela do perfil usa.
		mockMvc.perform(get("/users/" + mine.get("username").asText()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.aka").value("Java"));
	}

	@Test
	void clearsTheAkaWithAnEmptyString() throws Exception {
		var user = auth.authenticate("user_aka2");

		mockMvc.perform(patch("/me/profile")
				.header("X-Session-Token", user.token())
				.header("Idempotency-Key", "aka-2a")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("aka", "Java"))))
			.andExpect(status().isOk());

		mockMvc.perform(patch("/me/profile")
				.header("X-Session-Token", user.token())
				.header("Idempotency-Key", "aka-2b")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("aka", ""))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.aka").value(""));
	}

	@Test
	void refusesAnAkaAboveTheColumnLimit() throws Exception {
		var user = auth.authenticate("user_aka3");

		mockMvc.perform(patch("/me/profile")
				.header("X-Session-Token", user.token())
				.header("Idempotency-Key", "aka-3")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("aka", "a".repeat(61)))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("FIELD_TOO_LONG"));
	}
}
