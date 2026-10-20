package com.socialcoisa.rede.games.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

import com.socialcoisa.rede.shared.web.TestAuthHelper;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * A loja como o CRIADOR a administra: editar a ficha e a identidade visual (ícone/banner por foto
 * ou HTML), escolher o endereço do jogo (slug), excluir o jogo e ver os números de uso.
 *
 * Fecha o que faltava: antes só dava para enviar; editar era API sem tela, excluir não existia
 * (só suspender) e o número de "pessoas que jogaram" não aparecia em lugar nenhum.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GameStoreCustomizationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	private TestAuthHelper auth;

	@org.springframework.test.context.DynamicPropertySource
	static void adminAllowlist(org.springframework.test.context.DynamicPropertyRegistry registry) {
		registry.add("rede.admin.emails", () -> "gstore_admin@test.com");
	}

	@BeforeEach
	void setUp() {
		auth = new TestAuthHelper(mockMvc, objectMapper);
	}

	@Test
	void ownerEditsIdentityAndSlugAndDeletesTheGame() throws Exception {
		var creator = auth.authenticate("gstore_creator1");
		var other = auth.authenticate("gstore_other1");
		var admin = auth.authenticate("gstore_admin");

		String gameId = publish(creator, admin, "Jogo Editável", "https://8.8.8.8/editavel");
		String username = "gstore-creator1";

		// Edição completa: ficha, endereço novo e identidade visual (ícone + banner HTML).
		mockMvc.perform(patch("/games/" + gameId)
				.header("X-Session-Token", creator.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"title", "Jogo Editado",
					"url", "https://8.8.8.8/editado",
					"tags", "ARCADE",
					"slug", "Endereço Novo",
					"icon", "/uploads/jogos/icone.png",
					"banner", "/uploads/jogos/banner.png",
					"bannerHtml", "<div class=\"banner\">bem-vindo</div>"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.slug").value("endereco-novo"));

		// O endereço novo abre com a identidade visual; o velho deixa de existir.
		mockMvc.perform(get("/games/" + username + "/endereco-novo").header("X-Session-Token", creator.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.slug").value("endereco-novo"))
			.andExpect(jsonPath("$.icon").value("/uploads/jogos/icone.png"))
			.andExpect(jsonPath("$.banner").value("/uploads/jogos/banner.png"))
			.andExpect(jsonPath("$.bannerHtml").value("<div class=\"banner\">bem-vindo</div>"));
		mockMvc.perform(get("/games/" + username + "/jogo-editavel")).andExpect(status().isNotFound());

		// Endereço de outro jogo do MESMO criador é colisão: recusa, não sobrescreve.
		String segundo = publish(creator, admin, "Segundo Jogo", "https://8.8.8.8/segundo");
		mockMvc.perform(patch("/games/" + segundo)
				.header("X-Session-Token", creator.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"title", "Segundo Jogo", "url", "https://8.8.8.8/segundo", "slug", "endereco-novo"))))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SLUG_IN_USE"));

		// Excluir é do dono. Quem não é dono recebe 403.
		mockMvc.perform(delete("/games/" + gameId).header("X-Session-Token", other.token()))
			.andExpect(status().isForbidden());

		mockMvc.perform(delete("/games/" + gameId).header("X-Session-Token", creator.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DELETED"));

		// Some da loja, do endereço público, do painel e dos números.
		mockMvc.perform(get("/games/" + username + "/endereco-novo")).andExpect(status().isNotFound());
		mockMvc.perform(get("/me/games").header("X-Session-Token", creator.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.id == '" + gameId + "')]").isEmpty());
		mockMvc.perform(get("/games/" + gameId + "/analytics").header("X-Session-Token", creator.token()))
			.andExpect(status().isNotFound());
	}

	@Test
	void analyticsCountPublicViewsAndAreOwnerOnly() throws Exception {
		var creator = auth.authenticate("gstore_creator2");
		var player = auth.authenticate("gstore_player2");
		var admin = auth.authenticate("gstore_admin");

		String gameId = publish(creator, admin, "Jogo com Números", "https://8.8.8.8/numeros");
		String username = "gstore-creator2";

		// Duas aberturas públicas (anônimo + jogador) e UMA do dono — a do dono não conta.
		mockMvc.perform(get("/games/" + username + "/jogo-com-numeros")).andExpect(status().isOk());
		mockMvc.perform(get("/games/" + username + "/jogo-com-numeros")
				.header("X-Session-Token", player.token()))
			.andExpect(status().isOk());
		mockMvc.perform(get("/games/" + username + "/jogo-com-numeros")
				.header("X-Session-Token", creator.token()))
			.andExpect(status().isOk());

		mockMvc.perform(post("/games/" + gameId + "/play").header("X-Session-Token", player.token()))
			.andExpect(status().isOk());

		// O gráfico: 7 dias de janela fixa, totais e a série (só o criador vê).
		MvcResult result = mockMvc.perform(get("/games/" + gameId + "/analytics")
				.header("X-Session-Token", creator.token()).param("days", "7"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.plays").value(1))
			.andExpect(jsonPath("$.players").value(1))
			.andExpect(jsonPath("$.days").value(7))
			.andReturn();
		JsonNode analytics = objectMapper.readTree(result.getResponse().getContentAsString());
		JsonNode series = analytics.get("series");
		assertThat(series.size()).isEqualTo(7);
		JsonNode hoje = series.get(6);
		assertThat(hoje.get("pageViews").asInt()).isEqualTo(2);
		assertThat(hoje.get("plays").asInt()).isEqualTo(1);
		assertThat(hoje.get("players").asInt()).isEqualTo(1);

		// Números do jogo não são públicos.
		mockMvc.perform(get("/games/" + gameId + "/analytics").header("X-Session-Token", player.token()))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/games/" + gameId + "/analytics"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void creatorPanelPrefillsWithoutKnowingTheUsername() throws Exception {
		var creator = auth.authenticate("gstore_creator3");

		mockMvc.perform(put("/games/profile")
				.header("X-Session-Token", creator.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("displayName", "Estúdio Teste"))))
			.andExpect(status().isOk());

		mockMvc.perform(get("/games/profile/mine").header("X-Session-Token", creator.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.displayName").value("Estúdio Teste"))
			.andExpect(jsonPath("$.username").value("gstore-creator3"));
	}

	// --- apoio ---

	private String submit(TestAuthHelper.AuthSession creator, String title, String url) throws Exception {
		MvcResult result = mockMvc.perform(post("/games")
				.header("X-Session-Token", creator.token())
				.header("Idempotency-Key", "gstore-submit-" + title.hashCode())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("title", title, "url", url))))
			.andExpect(status().isOk())
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
	}

	private String publish(TestAuthHelper.AuthSession creator, TestAuthHelper.AuthSession admin, String title,
			String url) throws Exception {
		String gameId = submit(creator, title, url);
		mockMvc.perform(post("/admin/games/" + gameId + "/approve").header("X-Session-Token", admin.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of())))
			.andExpect(status().isOk());
		return gameId;
	}
}
