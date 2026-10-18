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
 * A identidade DO JOGO: token opaco de uso único (o jogo nunca vê a sessão), pseudônimo
 * estável e o gasto de faísca só com confirmação do lado da plataforma (idempotente).
 */
@SpringBootTest
@AutoConfigureMockMvc
class GameIdentityTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@org.springframework.test.context.DynamicPropertySource
	static void adminAllowlist(org.springframework.test.context.DynamicPropertyRegistry registry) {
		registry.add("rede.admin.emails", () -> "gid_admin@test.com");
	}

	@Test
	void identityTokenIsSingleUseAndSpendIsConfirmedAndIdempotent() throws Exception {
		String admin = new TestAuthHelper(mockMvc, objectMapper).authenticate("gid_admin").token();
		var player = auth("gid_player");
		openWallet(player.token());

		// Publica o jogo.
		MvcResult submitted = mockMvc.perform(post("/games")
				.header("X-Session-Token", player.token())
				.header("Idempotency-Key", "gid-submit")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"title", "Jogo de Identidade", "url", "https://player.itch.io/gid"))))
			.andExpect(status().isOk())
			.andReturn();
		String gameId = objectMapper.readTree(submitted.getResponse().getContentAsString()).get("id").asText();
		mockMvc.perform(post("/admin/games/" + gameId + "/approve")
				.header("X-Session-Token", admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("note", "ok"))))
			.andExpect(status().isOk());

		// Sem sessão, sem identidade.
		mockMvc.perform(post("/games/" + gameId + "/identity")).andExpect(status().isUnauthorized());

		// Emite o token + pseudônimo.
		MvcResult minted = mockMvc.perform(post("/games/" + gameId + "/identity")
				.header("X-Session-Token", player.token()))
			.andExpect(status().isOk())
			.andReturn();
		var mintedJson = objectMapper.readTree(minted.getResponse().getContentAsString());
		String token = mintedJson.get("token").asText();
		String playerRef = mintedJson.get("playerRef").asText();
		org.assertj.core.api.Assertions.assertThat(token).isNotBlank().hasSize(64);
		org.assertj.core.api.Assertions.assertThat(playerRef).startsWith("plr_");

		// O SERVIDOR DO JOGO troca o token pela identidade (uso único).
		mockMvc.perform(post("/games/tokens/validate")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("token", token))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.gameId").value(gameId))
			.andExpect(jsonPath("$.playerRef").value(playerRef));
		// Repetir o token recusa.
		mockMvc.perform(post("/games/tokens/validate")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("token", token))))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("TOKEN_ALREADY_USED"));

		// GASTO confirmado do NOSSO lado: 1.000 mf saem (genesis 105.000 -> 104.000).
		mockMvc.perform(post("/games/" + gameId + "/despesas")
				.header("X-Session-Token", player.token())
				.header("Idempotency-Key", "gid-spend")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"amount", 1_000, "motivo", "comprar sementes", "requestId", "req-1"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DEBITED"));
		MvcResult wallet = mockMvc.perform(get("/carteira").header("X-Session-Token", player.token()))
			.andExpect(status().isOk())
			.andReturn();
		org.assertj.core.api.Assertions.assertThat(
			objectMapper.readTree(wallet.getResponse().getContentAsString()).get("balance").asLong())
			.isEqualTo(104_000);

		// O MESMO pedido do jogo de novo: não cobra duas vezes (idempotente por requestId).
		mockMvc.perform(post("/games/" + gameId + "/despesas")
				.header("X-Session-Token", player.token())
				.header("Idempotency-Key", "gid-spend-2")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"amount", 1_000, "motivo", "comprar sementes", "requestId", "req-1"))))
			.andExpect(status().isOk());
		MvcResult wallet2 = mockMvc.perform(get("/carteira").header("X-Session-Token", player.token()))
			.andExpect(status().isOk())
			.andReturn();
		org.assertj.core.api.Assertions.assertThat(
			objectMapper.readTree(wallet2.getResponse().getContentAsString()).get("balance").asLong())
			.as("o pedido repetido não cobra de novo").isEqualTo(104_000);
	}

	private void openWallet(String token) throws Exception {
		mockMvc.perform(get("/carteira").header("X-Session-Token", token)).andExpect(status().isOk());
	}

	private TestAuthHelper.AuthSession auth(String name) throws Exception {
		return new TestAuthHelper(mockMvc, objectMapper).authenticate(name);
	}
}
