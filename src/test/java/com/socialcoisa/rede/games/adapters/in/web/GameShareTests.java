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
 * O G-share: a conquista compartilhada vira post no feed com a tag do jogo (o texto/mídia
 * vêm do jogo via postMessage e o APP publica com a sessão do jogador). Teto de 12/dia.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GameShareTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@org.springframework.test.context.DynamicPropertySource
	static void adminAllowlist(org.springframework.test.context.DynamicPropertyRegistry registry) {
		registry.add("rede.admin.emails", () -> "gshare_admin@test.com");
	}

	@Test
	void sharePublishesPostWithGameTagAndDailyCap() throws Exception {
		// O admin primeiro (o 4º signup bate o rate-limiter).
		String admin = new TestAuthHelper(mockMvc, objectMapper).authenticate("gshare_admin").token();
		var player = auth("gshare_player");
		var intruso = auth("gshare_intruso");
		openWallet(player.token());

		// Publica o jogo.
		MvcResult submitted = mockMvc.perform(post("/games")
				.header("X-Session-Token", player.token())
				.header("Idempotency-Key", "gshare-submit")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"title", "Fazendinha Alegre", "url", "https://player.itch.io/fazendinha"))))
			.andExpect(status().isOk())
			.andReturn();
		String gameId = objectMapper.readTree(submitted.getResponse().getContentAsString()).get("id").asText();
		mockMvc.perform(post("/admin/games/" + gameId + "/approve")
				.header("X-Session-Token", admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("note", "ok"))))
			.andExpect(status().isOk());

		// O jogador compartilha a conquista: o post nasce com a tag "game" e a do jogo.
		MvcResult shared = mockMvc.perform(post("/games/" + gameId + "/compartilhar")
				.header("X-Session-Token", player.token())
				.header("Idempotency-Key", "gshare-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("texto", "Peguei nível 10 na fazendinha!"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SHARED"))
			.andReturn();
		String postId = objectMapper.readTree(shared.getResponse().getContentAsString()).get("postId").asText();

		// O post está no feed público, com as tags game + slug.
		MvcResult feed = mockMvc.perform(get("/feed/posts/" + postId))
			.andExpect(status().isOk())
			.andReturn();
		String body = feed.getResponse().getContentAsString();
		org.assertj.core.api.Assertions.assertThat(body).contains("Peguei nível 10 na fazendinha!")
			.contains("\"game\"").contains("fazendinha-alegre");

		// Sem texto não compartilha.
		mockMvc.perform(post("/games/" + gameId + "/compartilhar")
				.header("X-Session-Token", player.token())
				.header("Idempotency-Key", "gshare-empty")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("texto", ""))))
			.andExpect(status().isBadRequest());

		// O teto diário: 11 compartilhamentos mais e o 13º recusa (429 SHARE_LIMIT).
		for (int i = 2; i <= 12; i++) {
			mockMvc.perform(post("/games/" + gameId + "/compartilhar")
					.header("X-Session-Token", player.token())
					.header("Idempotency-Key", "gshare-cap-" + i)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(Map.of("texto", "Conquista " + i + " da fazendinha!"))))
				.andExpect(status().isOk());
		}
		mockMvc.perform(post("/games/" + gameId + "/compartilhar")
				.header("X-Session-Token", player.token())
				.header("Idempotency-Key", "gshare-cap-13")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("texto", "A décima terceira conquista!"))))
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("SHARE_LIMIT"));
	}

	private void openWallet(String token) throws Exception {
		mockMvc.perform(get("/carteira").header("X-Session-Token", token)).andExpect(status().isOk());
	}

	private TestAuthHelper.AuthSession auth(String name) throws Exception {
		return new TestAuthHelper(mockMvc, objectMapper).authenticate(name);
	}
}
