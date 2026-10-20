package com.socialcoisa.rede.feed.adapters.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
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
 * A aplicação das preferências nos feeds (fase C do plano de extensão):
 * um post com assunto bloqueado sai do feed do leitor — mas continua no ar
 * para as outras pessoas e acessível direto. Post sem assunto nunca é bloqueado.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PersonalizationFeedTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	private TestAuthHelper auth;

	@BeforeEach
	void setUp() {
		auth = new TestAuthHelper(mockMvc, objectMapper);
	}

	private String createCommunity(TestAuthHelper.AuthSession session, String name, String topic)
			throws Exception {
		MvcResult result = mockMvc.perform(post("/communities")
				.header("X-Session-Token", session.token())
				.header("Idempotency-Key", "comm-" + name + "-" + System.nanoTime())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(java.util.Map.of(
					"name", name, "description", "Comunidade de teste", "topic", topic))))
			.andExpect(status().isCreated())
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
	}

	private String publishInCommunity(TestAuthHelper.AuthSession session, String communityId, String content)
			throws Exception {
		MvcResult result = mockMvc.perform(post("/feed/posts")
				.header("X-Session-Token", session.token())
				.header("Idempotency-Key", "pcomm-" + content.hashCode() + "-" + System.nanoTime())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(java.util.Map.of(
					"type", "INSIGHT", "content", content, "tags", java.util.List.of(),
					"mediaIds", java.util.List.of(), "communityId", communityId))))
			.andExpect(status().isCreated())
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
	}

	@Test
	void blockedTopicLeavesTheForYouFeedAndComesBack() throws Exception {
		var author = auth.authenticate("persfeed_author");
		var reader = auth.authenticate("persfeed_reader");

		// O autor cria uma comunidade de tecnologia, o leitor entra nela.
		String communityId = createCommunity(author, "PersFeed Tech", "TECNOLOGIA");
		mockMvc.perform(post("/communities/" + communityId + "/members")
				.header("X-Session-Token", reader.token())
				.header("Idempotency-Key", "join-persfeed"))
			.andExpect(status().isOk());

		// O autor publica na comunidade: o post herda o assunto TECNOLOGIA.
		String postId = publishInCommunity(author, communityId, "Post de tecnologia da comunidade.");

		// O leitor, sem bloqueio, vê o post no Para você (ele é membro).
		mockMvc.perform(get("/feed").param("tab", "ALL").header("X-Session-Token", reader.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[?(@.id == '" + postId + "')]").isNotEmpty());

		// Bloqueia o assunto: o post some do feed DO LEITOR.
		mockMvc.perform(put("/me/preferences")
				.header("X-Session-Token", reader.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(java.util.Map.of(
					"blockedTopics", java.util.List.of("TECNOLOGIA")))))
			.andExpect(status().isOk());

		mockMvc.perform(get("/feed").param("tab", "ALL").header("X-Session-Token", reader.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[?(@.id == '" + postId + "')]").isEmpty());

		// O AUTOR (sem bloqueio) continua vendo: o filtro é pessoal, não censura.
		mockMvc.perform(get("/feed").param("tab", "ALL").header("X-Session-Token", author.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[?(@.id == '" + postId + "')]").isNotEmpty());

		// Remove o bloqueio: o post volta.
		mockMvc.perform(put("/me/preferences")
				.header("X-Session-Token", reader.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(java.util.Map.of("blockedTopics", java.util.List.of()))))
			.andExpect(status().isOk());

		mockMvc.perform(get("/feed").param("tab", "ALL").header("X-Session-Token", reader.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[?(@.id == '" + postId + "')]").isNotEmpty());
	}
}
