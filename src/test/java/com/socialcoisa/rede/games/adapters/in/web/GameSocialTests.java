package com.socialcoisa.rede.games.adapters.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.socialcoisa.rede.games.application.port.out.GameRepository;
import com.socialcoisa.rede.games.domain.Game;
import com.socialcoisa.rede.shared.web.TestAuthHelper;

import tools.jackson.databind.ObjectMapper;

/** Avaliações e comentários na página do jogo. */
@SpringBootTest
@AutoConfigureMockMvc
class GameSocialTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private GameRepository games;

	@Test
	void reviewsAndCommentsFlow() throws Exception {
		var auth = new TestAuthHelper(mockMvc, objectMapper);
		var owner = auth.authenticate("social_game_owner");
		var reviewer = auth.authenticate("social_game_reviewer");

		String id = "game_social_" + UUID.randomUUID().toString().replace("-", "");
		Game game = Game.submit(id, owner.userId(), "social-" + id, "Jogo social", "desc", null,
			"https://example.com/jogo", "puzzle", Instant.now())
			.approve("admin", "ok", Instant.now());
		games.save(game);

		mockMvc.perform(post("/games/" + id + "/reviews")
				.header("X-Session-Token", reviewer.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("rating", 5, "body", "Muito bom!"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.rating").value(5));

		mockMvc.perform(get("/games/ratings").param("ids", id))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$['" + id + "'].average").value(5.0))
			.andExpect(jsonPath("$['" + id + "'].reviews").value(1));

		mockMvc.perform(get("/games/" + id + "/reviews"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.count").value(1))
			.andExpect(jsonPath("$.average").value(5.0))
			.andExpect(jsonPath("$.items[0].username").value("social-game-reviewer"));

		// O dono nao avalia o proprio jogo.
		mockMvc.perform(post("/games/" + id + "/reviews")
				.header("X-Session-Token", owner.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("rating", 5))))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("OWNER_CANNOT_REVIEW"));

		MvcResult comment = mockMvc.perform(post("/games/" + id + "/comments")
				.header("X-Session-Token", reviewer.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("body", "Tem multiplayer?"))))
			.andExpect(status().isOk())
			.andReturn();
		String commentId = objectMapper.readTree(comment.getResponse().getContentAsString()).get("id").asText();

		mockMvc.perform(get("/games/" + id + "/comments"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].body").value("Tem multiplayer?"));

		mockMvc.perform(delete("/games/" + id + "/comments/" + commentId)
				.header("X-Session-Token", reviewer.token()))
			.andExpect(status().isNoContent());

		mockMvc.perform(get("/games/" + id + "/comments"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(0));
	}
}
