package com.socialcoisa.rede.feed.adapters.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
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
import com.socialcoisa.rede.shared.web.TestDatabase;

/**
 * Post do tipo PAGE publicado DENTRO de uma comunidade.
 *
 * O relato foi "publicacao do tipo pagina em uma comunidade da erro: operacao conflita com o estado
 * atual dos dados" (409 vindo de DataIntegrityViolation). O caminho e o mesmo do feed (POST
 * /feed/posts com communityId), entao o teste cobre os dois tamanhos de pagina: uma pequena e uma
 * grande o bastante para estourar coluna de resposta/registro — o caso que ja mordeu antes.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CommunityPagePostTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	private TestAuthHelper auth;

	private String createdCommunityId;

	@BeforeEach
	void setUp() {
		auth = new TestAuthHelper(mockMvc, objectMapper);
	}

	@AfterEach
	void cleanUp() {
		if (createdCommunityId != null) {
			TestDatabase.deleteCommunity(createdCommunityId);
		}
	}

	@Test
	void publishesSmallPageInsideACommunity() throws Exception {
		var author = auth.authenticate("user_com_page1");
		String communityId = createCommunity(author, "Comunidade da pagina pequena");

		publishPage(author, communityId, "<h1>Pagina</h1><p>corpo da pagina</p>", "com-page-small")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.type").value("PAGE"))
			.andExpect(jsonPath("$.communityId").value(communityId));
	}

	@Test
	void publishesBigPageInsideACommunity() throws Exception {
		var author = auth.authenticate("user_com_page2");
		String communityId = createCommunity(author, "Comunidade da pagina grande");

		// ~12 mil caracteres: passa do limite de coluna de texto que ja quebrou a publicacao de
		// pagina uma vez (registro de idempotencia guardando a resposta).
		String bigHtml = "<div class=\"bloco\"><h2>Secao</h2><p>"
			+ "conteudo da pagina com texto suficiente para pesar. ".repeat(250)
			+ "</p></div>";

		publishPage(author, communityId, bigHtml, "com-page-big")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.type").value("PAGE"))
			.andExpect(jsonPath("$.communityId").value(communityId));
	}

	@Test
	void publishesPageAtTheSizeLimit() throws Exception {
		var author = auth.authenticate("user_com_page3");
		String communityId = createCommunity(author, "Comunidade da pagina no limite");

		// Perto do teto de 20 mil caracteres do campo.
		String html = "<div>" + "<p>paragrafo de teste com tamanho realista</p>".repeat(460) + "</div>";

		publishPage(author, communityId, html, "com-page-limit")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.type").value("PAGE"));
	}

	@Test
	void publishesPageWithEmojiAndControlCharacters() throws Exception {
		var author = auth.authenticate("user_com_page4");
		String communityId = createCommunity(author, "Comunidade da pagina com emoji");

		String html = "<h1>Minha pagina 🫵🚀</h1><p>texto com emoji e acentuação</p><p>tab\tcarriage\rnull\u0000char</p>";

		publishPage(author, communityId, html, "com-page-emoji")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.type").value("PAGE"));
	}

	private org.springframework.test.web.servlet.ResultActions publishPage(
			TestAuthHelper.AuthSession author, String communityId, String pageHtml, String idem) throws Exception {
		Map<String, Object> payload = new HashMap<>();
		payload.put("type", "PAGE");
		payload.put("content", "");
		payload.put("pageHtml", pageHtml);
		payload.put("tags", List.of());
		payload.put("mediaIds", List.of());
		payload.put("communityId", communityId);
		return mockMvc.perform(post("/feed/posts")
			.header("X-Session-Token", author.token())
			.header("Idempotency-Key", idem)
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(payload)));
	}

	private String createCommunity(TestAuthHelper.AuthSession owner, String name) throws Exception {
		String payload = objectMapper.writeValueAsString(Map.of(
			"name", name,
			"description", "Comunidade para o teste de publicacao de pagina.",
			"topic", "Tecnologia"));
		MvcResult result = mockMvc.perform(post("/communities")
				.header("X-Session-Token", owner.token())
				.header("Idempotency-Key", "create-comm-" + name)
				.contentType(MediaType.APPLICATION_JSON)
				.content(payload))
			.andExpect(status().isCreated())
			.andReturn();
		JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
		createdCommunityId = node.get("id").asText();
		return createdCommunityId;
	}
}
