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
 * "Jogados hoje": o card do feed lê as últimas 24 horas de PLAY_START, contados pelo servidor.
 *
 * As asserções comparam os DOIS jogos publicados por este teste entre si (e não "o primeiro do
 * ranking"): a janela é global, e outros testes da suíte também jogam — exigir o topo absoluto
 * seria exigir que o resto da suíte não existisse.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GameTrendingTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@org.springframework.test.context.DynamicPropertySource
	static void adminAllowlist(org.springframework.test.context.DynamicPropertyRegistry registry) {
		registry.add("rede.admin.emails", () -> "gtrend_admin@test.com");
	}

	@Test
	void ordersBySessionsAndCountsThem() throws Exception {
		String admin = new TestAuthHelper(mockMvc, objectMapper).authenticate("gtrend_admin").token();
		var creator = auth("gtrend_creator");

		String maisJogado = publish(admin, creator, "Jogo Mais Jogado", "https://creator.itch.io/maisa");
		String menosJogado = publish(admin, creator, "Jogo Menos Jogado", "https://creator.itch.io/menosa");

		for (int i = 0; i < 5; i++) {
			play(creator, maisJogado);
		}
		for (int i = 0; i < 2; i++) {
			play(creator, menosJogado);
		}

		// O card abre sem sessão nenhuma: quem acabou de chegar também vê o que está pegando.
		MvcResult response = mockMvc.perform(get("/games/hoje?limit=12"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.id == '" + maisJogado + "')].title").value("Jogo Mais Jogado"))
			.andReturn();
		String body = response.getResponse().getContentAsString();

		int mais = body.indexOf(maisJogado);
		int menos = body.indexOf(menosJogado);
		org.assertj.core.api.Assertions.assertThat(mais).as("os dois jogos entraram no ranking").isNotNegative();
		org.assertj.core.api.Assertions.assertThat(menos).as("os dois jogos entraram no ranking").isNotNegative();
		org.assertj.core.api.Assertions.assertThat(mais).as("mais jogado vem antes").isLessThan(menos);

		var entries = objectMapper.readTree(body);
		var entry = java.util.stream.StreamSupport.stream(entries.spliterator(), false)
			.filter(node -> maisJogado.equals(node.get("id").asText()))
			.findFirst()
			.orElseThrow();
		org.assertj.core.api.Assertions.assertThat(entry.get("plays").asLong()).isEqualTo(5);
		// Uma pessoa em cinco sessões é UMA pessoa: os dois números não são a mesma coisa.
		org.assertj.core.api.Assertions.assertThat(entry.get("players").asLong()).isEqualTo(1);
		org.assertj.core.api.Assertions.assertThat(entry.get("username").asText()).isEqualTo("gtrend-creator");
	}

	@Test
	void respectsTheLimit() throws Exception {
		var creator = auth("gtrend_limit");
		// O mesmo admin da outra cena: a allowlist do teste é uma conta só.
		String admin = new TestAuthHelper(mockMvc, objectMapper).authenticate("gtrend_admin").token();
		String game = publish(admin, creator, "Jogo Do Limite", "https://creator.itch.io/limita");
		play(creator, game);

		mockMvc.perform(get("/games/hoje?limit=1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1));
	}

	private void play(TestAuthHelper.AuthSession player, String gameId) throws Exception {
		mockMvc.perform(post("/games/" + gameId + "/play").header("X-Session-Token", player.token()))
			.andExpect(status().isOk());
	}

	private String publish(String admin, TestAuthHelper.AuthSession creator, String title, String url)
			throws Exception {
		MvcResult submitted = mockMvc.perform(post("/games")
				.header("X-Session-Token", creator.token())
				.header("Idempotency-Key", "gtrend-" + title)
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

	private TestAuthHelper.AuthSession auth(String name) throws Exception {
		return new TestAuthHelper(mockMvc, objectMapper).authenticate(name);
	}
}
