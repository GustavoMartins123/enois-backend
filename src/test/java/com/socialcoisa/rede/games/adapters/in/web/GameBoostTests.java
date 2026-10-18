package com.socialcoisa.rede.games.adapters.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.ObjectMapper;

import com.socialcoisa.rede.shared.web.TestAuthHelper;

/**
 * O destaque pago (G2): o criador impulsiona o PRÓPRIO jogo (5 faíscas/7 dias, um por
 * semana); o catálogo sobe o jogo impulsionado; não-criador não impulsa.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GameBoostTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@org.springframework.test.context.DynamicPropertySource
	static void adminAllowlist(org.springframework.test.context.DynamicPropertyRegistry registry) {
		registry.add("rede.admin.emails", () -> "gboost_admin@test.com");
	}

	@Test
	void boostPutsGameFirstAndOnePerWeek() throws Exception {
		// Admin autenticado primeiro (o 4º signup bate o rate-limiter).
		String admin = new TestAuthHelper(mockMvc, objectMapper).authenticate("gboost_admin").token();
		var creator = auth("gboost_creator");
		var outro = auth("gboost_outro");
		openWallet(creator.token());
		openWallet(outro.token());

		// Publica o jogo (envio + aprovação).
		MvcResult submitted = mockMvc.perform(post("/games")
				.header("X-Session-Token", creator.token())
				.header("Idempotency-Key", "gboost-submit")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"title", "Jogo Impulsionado", "url", "https://creator.itch.io/impulsionado"))))
			.andExpect(status().isOk())
			.andReturn();
		String gameId = objectMapper.readTree(submitted.getResponse().getContentAsString()).get("id").asText();
		mockMvc.perform(post("/admin/games/" + gameId + "/approve")
				.header("X-Session-Token", admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("note", "ok"))))
			.andExpect(status().isOk());

		// Não-criador não impulsa.
		mockMvc.perform(post("/games/" + gameId + "/boost").header("X-Session-Token", outro.token()))
			.andExpect(status().isForbidden());

		// O criador impulsa: 5.000 mf saem (105.000 -> 100.000) e o jogo fica em destaque.
		mockMvc.perform(post("/games/" + gameId + "/boost").header("X-Session-Token", creator.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.boosted").value(true));
		MvcResult wallet = mockMvc.perform(get("/carteira").header("X-Session-Token", creator.token()))
			.andExpect(status().isOk())
			.andReturn();
		org.assertj.core.api.Assertions.assertThat(
			objectMapper.readTree(wallet.getResponse().getContentAsString()).get("balance").asLong())
			.isEqualTo(100_000);

		// O segundo impulso na mesma semana recusa (um por semana por jogo).
		mockMvc.perform(post("/games/" + gameId + "/boost").header("X-Session-Token", creator.token()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ALREADY_BOOSTED"));

		// O catálogo sobe o impulsionado primeiro (order=recent é o padrão).
		MvcResult catalog = mockMvc.perform(get("/games?limit=60"))
			.andExpect(status().isOk())
			.andReturn();
		String body = catalog.getResponse().getContentAsString();
		org.assertj.core.api.Assertions.assertThat(body.indexOf(gameId))
			.as("o jogo impulsionado aparece no catálogo").isGreaterThan(-1);
		if (body.contains("\"items\":[")) {
			org.assertj.core.api.Assertions.assertThat(body.indexOf(gameId) - body.indexOf("\"items\":["))
				.as("primeiro entre os itens").isLessThan(400);
		}
	}

	private void openWallet(String token) throws Exception {
		mockMvc.perform(get("/carteira").header("X-Session-Token", token)).andExpect(status().isOk());
	}

	private TestAuthHelper.AuthSession auth(String name) throws Exception {
		return new TestAuthHelper(mockMvc, objectMapper).authenticate(name);
	}
}
