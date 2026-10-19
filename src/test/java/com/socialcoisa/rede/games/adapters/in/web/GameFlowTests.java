package com.socialcoisa.rede.games.adapters.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.socialcoisa.rede.shared.web.TestAuthHelper;

/**
 * O fluxo do agregador de jogos: criador envia o LINK, o jogo entra na fila de revisão,
 * a moderação aprova (publica no catálogo e na página do criador) ou recusa com motivo.
 * O Énóis NÃO hospeda o jogo — o link é https e roda embutido no front.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GameFlowTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private com.socialcoisa.rede.profile.application.port.out.UserProfileRepository profiles;

	@org.springframework.test.context.DynamicPropertySource
	static void adminAllowlist(org.springframework.test.context.DynamicPropertyRegistry registry) {
		registry.add("rede.admin.emails", () -> "gflow_admin@test.com");
	}

	@Test
	void submitReviewPublishFlow() throws Exception {
		var creator = auth("gflow_creator");
		var admin = auth("gflow_admin");

		// Link http recusado.
		mockMvc.perform(post("/games")
				.header("X-Session-Token", creator.token())
				.header("Idempotency-Key", "gflow-http")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"title", "Kawaii Survivors", "url", "http://meusite.com/kawaii"))))
			.andExpect(status().isBadRequest());

		// Envio válido → SUBMITTED.
		MvcResult submitted = mockMvc.perform(post("/games")
				.header("X-Session-Token", creator.token())
				.header("Idempotency-Key", "gflow-submit")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"title", "Kawaii Survivors", "url", "https://aminadojava.itch.io/kawaii-survivors",
					"description", "Survivor fofo com pixel art.",
					"tags", "arcade,pixel"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SUBMITTED"))
			.andReturn();
		String gameId = objectMapper.readTree(submitted.getResponse().getContentAsString()).get("id").asText();

		// Catálogo público NÃO mostra ainda (não publicado). O catálogo vem paginado (items).
		mockMvc.perform(get("/games?limit=60"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[?(@.id == '" + gameId + "')]").isEmpty());

		// Página do criador mostra o jogo PRÓPRIO mesmo não publicado (dono vê o estado).
		mockMvc.perform(get("/me/games").header("X-Session-Token", creator.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.id == '" + gameId + "')].status").value("SUBMITTED"));

		// Moderação aprova → PUBLISHED.
		mockMvc.perform(post("/admin/games/" + gameId + "/approve")
				.header("X-Session-Token", admin.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("note", "carrega e roda"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PUBLISHED"));

		// Agora aparece no catálogo e na página do criador.
		mockMvc.perform(get("/games?limit=60"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[?(@.id == '" + gameId + "')].status").value("PUBLISHED"));
		mockMvc.perform(get("/games/gflow-creator"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.id == '" + gameId + "')].url").value("https://aminadojava.itch.io/kawaii-survivors"));
	}

	@Test
	void rejectRequiresNoteAndCreatorSeesReason() throws Exception {
		var creator = auth("gflow_creator2");
		var admin = auth("gflow_admin");

		MvcResult submitted = mockMvc.perform(post("/games")
				.header("X-Session-Token", creator.token())
				.header("Idempotency-Key", "gflow2-submit")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"title", "Jogo Incompleto", "url", "https://meusite.com/jogo-incompleto"))))
			.andExpect(status().isOk())
			.andReturn();
		String gameId = objectMapper.readTree(submitted.getResponse().getContentAsString()).get("id").asText();

		// Recusa sem motivo recusa a própria recusa.
		mockMvc.perform(post("/admin/games/" + gameId + "/reject")
				.header("X-Session-Token", admin.token()))
			.andExpect(status().isBadRequest());

		mockMvc.perform(post("/admin/games/" + gameId + "/reject")
				.header("X-Session-Token", admin.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("note", "O link não abre o jogo."))))
			.andExpect(status().isOk());

		// O criador vê o motivo no próprio jogo.
		mockMvc.perform(get("/me/games").header("X-Session-Token", creator.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.id == '" + gameId + "')].reviewNote").value("O link não abre o jogo."));
	}

	@Test
	void anonymousCanBrowseCatalogButNotSubmit() throws Exception {
		mockMvc.perform(get("/games?limit=5"))
			.andExpect(status().isOk());

		mockMvc.perform(post("/games")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("title", "Anônimo", "url", "https://x.com"))))
			.andExpect(status().isUnauthorized());
	}

	/**
	 * Página de criador VAZIA não é presença de dev: o dono enxerga a própria página (é a rota
	 * do painel), mas ninguém mais — e salvar em branco não "abre" a página para os outros.
	 * (O selo "Dev de jogos" no perfil depende exatamente disto.)
	 */
	@Test
	void emptyDeveloperPageIsOnlyVisibleToItsOwner() throws Exception {
		var dono = auth("gflow_empty_dev");
		var outra = auth("gflow_other_viewer");
		// O username é derivado no cadastro (slug/unicidade) — nunca presumir a forma final.
		String username = profiles.findById(dono.userId()).orElseThrow().username();

		// O dono vê a própria página, mesmo sem jogo e sem nada preenchido.
		mockMvc.perform(get("/games/" + username + "/profile").header("X-Session-Token", dono.token()))
			.andExpect(status().isOk());
		// Para qualquer outra pessoa (e para visitante), não existe página.
		mockMvc.perform(get("/games/" + username + "/profile").header("X-Session-Token", outra.token()))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/games/" + username + "/profile"))
			.andExpect(status().isNotFound());

		// Salvar em branco cria/atualiza a linha, mas não conta como conteúdo.
		mockMvc.perform(put("/games/profile")
				.header("X-Session-Token", dono.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isOk());
		mockMvc.perform(get("/games/" + username + "/profile").header("X-Session-Token", outra.token()))
			.andExpect(status().isNotFound());

		// Com conteúdo preenchido, a página passa a existir para todo mundo.
		mockMvc.perform(put("/games/profile")
				.header("X-Session-Token", dono.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("displayName", "Estúdio Kawaii"))))
			.andExpect(status().isOk());
		mockMvc.perform(get("/games/" + username + "/profile").header("X-Session-Token", outra.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.displayName").value("Estúdio Kawaii"));
	}

	private TestAuthHelper.AuthSession auth(String name) throws Exception {
		return new TestAuthHelper(mockMvc, objectMapper).authenticate(name);
	}
}
