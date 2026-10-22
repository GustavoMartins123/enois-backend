package com.socialcoisa.rede.games.adapters.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
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
 * G3: coleções temáticas curadas — a administração cria, o catálogo público lê com os
 * jogos RESOLVIDOS (publicados apenas), e esconder tira do ar.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GameCollectionTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@org.springframework.test.context.DynamicPropertySource
	static void adminAllowlist(org.springframework.test.context.DynamicPropertyRegistry registry) {
		registry.add("rede.admin.emails", () -> "gcol_admin@test.com");
	}

	@Test
	void createReadAndHide() throws Exception {
		String admin = new TestAuthHelper(mockMvc, objectMapper).authenticate("gcol_admin").token();
		var creator = auth("gcol_creator");
		openWallet(creator.token());

		// Publica dois jogos.
		String jogo1 = publish(admin, creator, "Jogo Coleção A", "https://creator.itch.io/coleta");
		String jogo2 = publish(admin, creator, "Jogo Coleção B", "https://creator.itch.io/coletb");

		// Sem sessão de admin não cria.
		mockMvc.perform(post("/games/colecoes")
				.header("X-Session-Token", creator.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"title", "Coleção da comunidade", "gameIds", List.of(jogo1)))))
			.andExpect(status().isForbidden());

		// Cria a coleção com os dois jogos.
		MvcResult created = mockMvc.perform(post("/games/colecoes")
				.header("X-Session-Token", admin)
				.header("Idempotency-Key", "gcol-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"title", "Pixel quentinho", "description", "Jogos curados da semana",
					"gameIds", List.of(jogo1, jogo2)))))
			.andExpect(status().isOk())
			.andReturn();
		String colecaoId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

		// A leitura pública traz os jogos RESOLVIDOS (títulos, não só ids).
		MvcResult catalog = mockMvc.perform(get("/games/colecoes?limit=20"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.id == '" + colecaoId + "')].title").value("Pixel quentinho"))
			.andReturn();
		String body = catalog.getResponse().getContentAsString();
		org.assertj.core.api.Assertions.assertThat(body).contains("Jogo Coleção A").contains("Jogo Coleção B");

		// Coleção sem jogo nenhum não existe.
		mockMvc.perform(post("/games/colecoes")
				.header("X-Session-Token", admin)
				.header("Idempotency-Key", "gcol-2")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("title", "Coleção vazia", "gameIds", List.of()))))
			.andExpect(status().isBadRequest());

		// Esconder tira do ar.
		mockMvc.perform(post("/games/colecoes/" + colecaoId + "/esconder").header("X-Session-Token", admin))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("HIDDEN"));
		mockMvc.perform(get("/games/colecoes?limit=20"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.id == '" + colecaoId + "')]").isEmpty());
	}

	private String publish(String admin, TestAuthHelper.AuthSession creator, String title, String url)
			throws Exception {
		MvcResult submitted = mockMvc.perform(post("/games")
				.header("X-Session-Token", creator.token())
				.header("Idempotency-Key", "gcol-" + title)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("title", title, "url", url))))
			.andExpect(status().isOk())
			.andReturn();
		String gameId = objectMapper.readTree(submitted.getResponse().getContentAsString()).get("id").asText();
		mockMvc.perform(post("/admin/games/" + gameId + "/approve")
				.header("X-Session-Token", admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("note", "ok"))))
			.andExpect(status().isOk());
		return gameId;
	}

	private void openWallet(String token) throws Exception {
		mockMvc.perform(get("/carteira").header("X-Session-Token", token)).andExpect(status().isOk());
	}

	private TestAuthHelper.AuthSession auth(String name) throws Exception {
		return new TestAuthHelper(mockMvc, objectMapper).authenticate(name);
	}
}
