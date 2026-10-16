package com.socialcoisa.rede.feed.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
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
 * Post do tipo PAGE: o autor cola um HTML e ele vira uma mini pagina. O backend guarda o HTML
 * ja sanitizado, entao o teste cobre publicacao, edicao e os limites — e confirma que HTML em
 * posts de outros tipos e descartado em vez de virar lixo no banco.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PostPageTests {

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
	void publishesPageWithSanitizedHtml() throws Exception {
		var author = auth.authenticate("user_page_owner1");

		MvcResult created = mockMvc.perform(post("/feed/posts")
				.header("X-Session-Token", author.token())
				.header("Idempotency-Key", "page-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(Map.of(
					"type", "PAGE",
					"content", "Minha pagina",
					"pageHtml", "<h1>Ola</h1><script>fetch('https://malicioso.test')</script>"
						+ "<p onclick=\"alert(1)\" style=\"color:#123456\">corpo</p>"))))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.type").value("PAGE"))
			.andExpect(jsonPath("$.content").value("Minha pagina"))
			.andReturn();

		JsonNode node = objectMapper.readTree(created.getResponse().getContentAsString());
		String pageHtml = node.get("pageHtml").asText();
		assertThat(pageHtml).contains("<h1>Ola</h1>").contains("corpo").contains("#123456");
		assertThat(pageHtml).doesNotContain("<script").doesNotContain("onclick").doesNotContain("malicioso.test");

		// A leitura publica devolve o mesmo HTML limpo (nao ha versao crua guardada em lugar nenhum).
		String postId = node.get("id").asText();
		mockMvc.perform(get("/feed/posts/" + postId).header("X-Session-Token", author.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.pageHtml").value(pageHtml));
	}

	@Test
	void pageWithoutHtmlIsRejected() throws Exception {
		var author = auth.authenticate("user_page_owner2");

		mockMvc.perform(post("/feed/posts")
				.header("X-Session-Token", author.token())
				.header("Idempotency-Key", "page-2")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(Map.of("type", "PAGE", "content", "Pagina sem corpo", "pageHtml", "   "))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_PAGE_HTML"));
	}

	@Test
	void htmlSentOnAnotherTypeIsDiscarded() throws Exception {
		var author = auth.authenticate("user_page_owner3");

		MvcResult created = mockMvc.perform(post("/feed/posts")
				.header("X-Session-Token", author.token())
				.header("Idempotency-Key", "page-3")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(Map.of(
					"type", "INSIGHT",
					"content", "Post normal com html na mochila",
					"pageHtml", "<p>nao deveria ser guardado</p>"))))
			.andExpect(status().isCreated())
			.andReturn();

		JsonNode node = objectMapper.readTree(created.getResponse().getContentAsString());
		JsonNode pageHtml = node.get("pageHtml");
		assertThat(pageHtml == null || pageHtml.isNull()).isTrue();
	}

	@Test
	void editReplacesPageAndRequiresIt() throws Exception {
		var author = auth.authenticate("user_page_owner4");
		String postId = publishPage(author, "Pagina original", "<p>versao 1</p>", "page-edit-pub");

		mockMvc.perform(patch("/feed/posts/" + postId)
				.header("X-Session-Token", author.token())
				.header("Idempotency-Key", "page-edit-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(Map.of("content", "Pagina revisada", "pageHtml", "<p>versao 2</p><script>x()</script>"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content").value("Pagina revisada"))
			.andExpect(jsonPath("$.pageHtml").value("<p>versao 2</p>"));

		// Editar uma pagina sem mandar HTML apagaria o corpo do post: o backend recusa.
		mockMvc.perform(patch("/feed/posts/" + postId)
				.header("X-Session-Token", author.token())
				.header("Idempotency-Key", "page-edit-2")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(Map.of("content", "Pagina revisada"))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_PAGE_HTML"));
	}

	@Test
	void oversizedPageIsRejected() throws Exception {
		var author = auth.authenticate("user_page_owner5");

		mockMvc.perform(post("/feed/posts")
				.header("X-Session-Token", author.token())
				.header("Idempotency-Key", "page-5")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(Map.of(
					"type", "PAGE",
					"content", "Pagina grande demais",
					"pageHtml", "<p>" + "a".repeat(12_000) + "</p>"))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("PAGE_HTML_TOO_LONG"));
	}

	private String publishPage(TestAuthHelper.AuthSession session, String title, String html, String key)
			throws Exception {
		MvcResult result = mockMvc.perform(post("/feed/posts")
				.header("X-Session-Token", session.token())
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(Map.of("type", "PAGE", "content", title, "pageHtml", html))))
			.andExpect(status().isCreated())
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
	}

	/** Map.of nao aceita nulo; aqui os campos sao todos nao nulos, mas a lista de tags fica explicita. */
	private String json(Map<String, Object> body) throws Exception {
		Map<String, Object> payload = new HashMap<>(body);
		payload.putIfAbsent("tags", List.of());
		payload.putIfAbsent("mediaIds", List.of());
		return objectMapper.writeValueAsString(payload);
	}
}
