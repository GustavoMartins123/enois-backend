package com.socialcoisa.rede.feed.adapters.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

import com.socialcoisa.rede.shared.web.TestAuthHelper;

import tools.jackson.databind.ObjectMapper;

/** Republicar: cópia com crédito, sem duplicata, apontando sempre para o original. */
@SpringBootTest
@AutoConfigureMockMvc
class RepostFlowTests {

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
	void repostCopiesWithCreditBlocksDuplicatesAndFollowsTheRoot() throws Exception {
		var author = auth.authenticate("repost_author_" + System.nanoTime());
		var reposter = auth.authenticate("repost_user_" + System.nanoTime());
		var other = auth.authenticate("repost_other_" + System.nanoTime());

		String originalId = publish(author, "Conteúdo original que vale republicar.");
		String authorUsername = username(author);

		MvcResult created = mockMvc.perform(post("/feed/posts/{id}/repost", originalId)
				.header("X-Session-Token", reposter.token()))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.repostOf").value(originalId))
			.andExpect(jsonPath("$.repostOfAuthor").value(authorUsername))
			.andExpect(jsonPath("$.content").value("Conteúdo original que vale republicar."))
			.andReturn();
		String repostId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asString();

		// A mesma pessoa não republica o mesmo post duas vezes.
		mockMvc.perform(post("/feed/posts/{id}/repost", originalId)
				.header("X-Session-Token", reposter.token()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ALREADY_REPOSTED"));

		// Republicar a republicação aponta para o ORIGINAL (sem corrente de créditos).
		mockMvc.perform(post("/feed/posts/{id}/repost", repostId)
				.header("X-Session-Token", other.token()))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.repostOf").value(originalId));

		// Apagar o original apaga as cópias.
		mockMvc.perform(delete("/feed/posts/{id}", originalId)
				.header("X-Session-Token", author.token())
				.header("Idempotency-Key", "repost-del-" + System.nanoTime()))
			.andExpect(status().isOk());
		mockMvc.perform(get("/feed/posts/{id}", repostId))
			.andExpect(status().isNotFound());
	}

	@Test
	void ephemeralPostsCannotBeReposted() throws Exception {
		var author = auth.authenticate("repost_moment_" + System.nanoTime());
		var reposter = auth.authenticate("repost_moment_user_" + System.nanoTime());

		MvcResult moment = mockMvc.perform(post("/feed/posts")
				.header("X-Session-Token", author.token())
				.header("Idempotency-Key", "repost-moment-" + System.nanoTime())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"type", "MOMENT", "content", "Um momento que some em 24h.", "tags", List.of(),
					"mediaIds", List.of()))))
			.andExpect(status().isCreated())
			.andReturn();
		String momentId = objectMapper.readTree(moment.getResponse().getContentAsString()).get("id").asString();

		mockMvc.perform(post("/feed/posts/{id}/repost", momentId)
				.header("X-Session-Token", reposter.token()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("NOT_REPOSTABLE"));
	}

	private String publish(TestAuthHelper.AuthSession session, String content) throws Exception {
		MvcResult result = mockMvc.perform(post("/feed/posts")
				.header("X-Session-Token", session.token())
				.header("Idempotency-Key", "repost-pub-" + System.nanoTime())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"type", "INSIGHT", "content", content, "tags", List.of(), "mediaIds", List.of()))))
			.andExpect(status().isCreated())
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asString();
	}

	private String username(TestAuthHelper.AuthSession session) throws Exception {
		MvcResult me = mockMvc.perform(get("/me").header("X-Session-Token", session.token()))
			.andExpect(status().isOk())
			.andReturn();
		return objectMapper.readTree(me.getResponse().getContentAsString()).get("username").asString();
	}
}
