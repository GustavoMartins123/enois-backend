package com.socialcoisa.rede.feed.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import tools.jackson.databind.ObjectMapper;
import com.socialcoisa.rede.shared.web.TestAuthHelper;

/**
 * Edicao de publicacao propria: so o autor edita, com as mesmas regras de conteudo do post novo.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EditPostTests {

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
	void ownerEditsContentAndEditedAtIsRecorded() throws Exception {
		var author = auth.authenticate("user_edit_owner");
		String postId = publish(author, "Texto original do post.");

		mockMvc.perform(patch("/feed/posts/" + postId)
				.header("X-Session-Token", author.token())
				.header("Idempotency-Key", "edit-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("content", "Texto revisado.", "tags", List.of("java")))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content").value("Texto revisado."))
			.andExpect(jsonPath("$.tags[0]").value("java"))
			.andExpect(jsonPath("$.editedAt").isNotEmpty());

		// A leitura seguinte ja vem com o texto novo e a marca de edicao.
		MvcResult read = mockMvc.perform(get("/feed/posts/" + postId).header("X-Session-Token", author.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content").value("Texto revisado."))
			.andReturn();
		assertThat(objectMapper.readTree(read.getResponse().getContentAsString()).has("editedAt")).isTrue();
	}

	@Test
	void anotherUserCannotEdit() throws Exception {
		var author = auth.authenticate("user_edit_author2");
		var intruder = auth.authenticate("user_edit_intruder");
		String postId = publish(author, "Post que nao pertence ao intruso.");

		mockMvc.perform(patch("/feed/posts/" + postId)
				.header("X-Session-Token", intruder.token())
				.header("Idempotency-Key", "edit-intrude-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("content", "Tentativa de edicao alheia."))))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("POST_OWNER_MISMATCH"));
	}

	@Test
	void emptyContentIsRejected() throws Exception {
		var author = auth.authenticate("user_edit_author3");
		String postId = publish(author, "Conteudo que sera esvaziado.");

		mockMvc.perform(patch("/feed/posts/" + postId)
				.header("X-Session-Token", author.token())
				.header("Idempotency-Key", "edit-empty-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("content", "   "))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_CONTENT"));
	}

	@Test
	void tooLongContentIsRejected() throws Exception {
		var author = auth.authenticate("user_edit_author4");
		String postId = publish(author, "Conteudo normal.");

		mockMvc.perform(patch("/feed/posts/" + postId)
				.header("X-Session-Token", author.token())
				.header("Idempotency-Key", "edit-long-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("content", "a".repeat(1201)))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("CONTENT_TOO_LONG"));
	}

	private String publish(TestAuthHelper.AuthSession session, String content) throws Exception {
		MvcResult result = mockMvc.perform(post("/feed/posts")
				.header("X-Session-Token", session.token())
				.header("Idempotency-Key", "pub-edit-" + content.hashCode())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"type", "INSIGHT", "content", content, "tags", List.of(), "mediaIds", List.of()))))
			.andExpect(status().isCreated())
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
	}
}