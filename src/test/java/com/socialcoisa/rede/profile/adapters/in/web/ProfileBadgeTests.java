package com.socialcoisa.rede.profile.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
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
 * Emblema do perfil: PNG pequeno que aparece ao lado do nome, guardado como URL de midia (igual
 * avatar e capa).
 *
 * O que este teste protege: a leitura de volta (a UI depende dela), o "limpar" com string vazia —
 * o servidor preserva o valor quando o campo vem ausente, entao vazio e a unica forma de remover —
 * e o teto da coluna, que faltava no `validateLengths` e deixava o valor estourar no banco.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProfileBadgeTests {

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
	void savesABadgeAndReadsItBack() throws Exception {
		var user = auth.authenticate("user_badge1");
		String url = "https://ik.imagekit.io/x/emblema.png";

		mockMvc.perform(patch("/me/profile")
				.header("X-Session-Token", user.token())
				.header("Idempotency-Key", "badge-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("badge", url))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.badge").value(url));

		MvcResult read = mockMvc.perform(get("/me").header("X-Session-Token", user.token()))
			.andExpect(status().isOk())
			.andReturn();
		JsonNode badge = objectMapper.readTree(read.getResponse().getContentAsString()).get("badge");
		assertThat(badge.asText()).isEqualTo(url);
	}

	@Test
	void clearsTheBadgeWithAnEmptyString() throws Exception {
		var user = auth.authenticate("user_badge2");

		mockMvc.perform(patch("/me/profile")
				.header("X-Session-Token", user.token())
				.header("Idempotency-Key", "badge-2a")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("badge", "https://ik.imagekit.io/x/e.png"))))
			.andExpect(status().isOk());

		mockMvc.perform(patch("/me/profile")
				.header("X-Session-Token", user.token())
				.header("Idempotency-Key", "badge-2b")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("badge", ""))))
			.andExpect(status().isOk());

		MvcResult read = mockMvc.perform(get("/me").header("X-Session-Token", user.token()))
			.andExpect(status().isOk())
			.andReturn();
		JsonNode badge = objectMapper.readTree(read.getResponse().getContentAsString()).get("badge");
		assertThat(badge.asText()).isEmpty();
	}

	@Test
	void keepsTheBadgeWhenTheFieldIsAbsent() throws Exception {
		var user = auth.authenticate("user_badge3");
		String url = "https://ik.imagekit.io/x/fica.png";

		mockMvc.perform(patch("/me/profile")
				.header("X-Session-Token", user.token())
				.header("Idempotency-Key", "badge-3a")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("badge", url))))
			.andExpect(status().isOk());

		// Edicao de outro campo, sem tocar no emblema: o valor tem de sobreviver.
		Map<String, Object> other = new HashMap<>();
		other.put("headline", "Mudou so a headline");
		mockMvc.perform(patch("/me/profile")
				.header("X-Session-Token", user.token())
				.header("Idempotency-Key", "badge-3b")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(other)))
			.andExpect(status().isOk());

		MvcResult read = mockMvc.perform(get("/me").header("X-Session-Token", user.token()))
			.andExpect(status().isOk())
			.andReturn();
		JsonNode badge = objectMapper.readTree(read.getResponse().getContentAsString()).get("badge");
		assertThat(badge.asText()).isEqualTo(url);
	}

	@Test
	void rejectsABadgeAboveTheColumnLimit() throws Exception {
		var user = auth.authenticate("user_badge4");

		mockMvc.perform(patch("/me/profile")
				.header("X-Session-Token", user.token())
				.header("Idempotency-Key", "badge-4")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("badge", "a".repeat(501)))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("FIELD_TOO_LONG"))
			.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("500")));
	}
}
