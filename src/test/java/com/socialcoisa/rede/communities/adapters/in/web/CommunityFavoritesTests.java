package com.socialcoisa.rede.communities.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
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
 * Favoritar (bookmark) comunidade: independe de participar e a lista reflete o que foi salvo.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CommunityFavoritesTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	private TestAuthHelper auth;

	private String communityId;

	@BeforeEach
	void setUp() {
		auth = new TestAuthHelper(mockMvc, objectMapper);
	}

	@AfterEach
	void cleanUp() {
		if (communityId != null) {
			TestDatabase.deleteCommunity(communityId);
		}
	}

	@Test
	void favoritesAndUnfavoritesACommunity() throws Exception {
		var owner = auth.authenticate("fav_owner");
		var viewer = auth.authenticate("fav_viewer");
		communityId = createCommunity(owner, "Comunidade para favoritar");

		// Favoritar sem participar e permitido (e um bookmark).
		favorite(viewer, communityId, true);
		assertThat(favoriteIds(viewer)).contains(communityId);

		// Favoritar de novo nao duplica.
		favorite(viewer, communityId, true);
		assertThat(favoriteIds(viewer)).contains(communityId);

		// Desfavoritar tira da lista.
		favorite(viewer, communityId, false);
		assertThat(favoriteIds(viewer)).doesNotContain(communityId);
	}

	private void favorite(TestAuthHelper.AuthSession session, String id, boolean on) throws Exception {
		mockMvc.perform((on ? post("/communities/" + id + "/favorite") : delete("/communities/" + id + "/favorite"))
				.header("X-Session-Token", session.token())
				.header("Idempotency-Key", (on ? "fav-" : "unfav-") + id))
			.andExpect(status().isOk());
	}

	private List<String> favoriteIds(TestAuthHelper.AuthSession session) throws Exception {
		MvcResult result = mockMvc.perform(get("/communities/favorites").header("X-Session-Token", session.token()))
			.andExpect(status().isOk())
			.andReturn();
		JsonNode items = objectMapper.readTree(result.getResponse().getContentAsString()).get("items");
		List<String> ids = new ArrayList<>();
		for (int i = 0; i < items.size(); i++) ids.add(items.get(i).get("id").asText());
		return ids;
	}

	private String createCommunity(TestAuthHelper.AuthSession owner, String name) throws Exception {
		MvcResult result = mockMvc.perform(post("/communities")
				.header("X-Session-Token", owner.token())
				.header("Idempotency-Key", "create-" + name.hashCode())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", name, "description", "Descricao.", "topic", "TECNOLOGIA"))))
			.andExpect(status().isCreated())
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
	}
}