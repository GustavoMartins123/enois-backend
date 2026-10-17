package com.socialcoisa.rede.feed.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
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

import tools.jackson.databind.ObjectMapper;
import com.socialcoisa.rede.shared.web.TestAuthHelper;

/**
 * Selos de post: a marcacao no topo, ao lado da reacao.
 *
 * O que este teste tranca: um selo por pessoa por post (clicar de novo remove, clicar em outro
 * troca), a contagem por selo na leitura em lote, e a leitura publica (sem sessao) mostrando os
 * selos sem o "meu".
 */
@SpringBootTest
@AutoConfigureMockMvc
class PostSealTests {

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
	void oneSealPerPersonAndCountsInTheBatchRead() throws Exception {
		var author = auth.authenticate("seal_author1");
		var first = auth.authenticate("seal_first1");
		var second = auth.authenticate("seal_second1");

		String postId = publish(author, "Post que vai receber selos");

		// Duas pessoas marcam o mesmo selo.
		seal(first, postId, "IMPORTANTE").andExpect(status().isOk());
		seal(second, postId, "IMPORTANTE").andExpect(status().isOk());
		// E uma delas troca para outro: continua valendo UM por pessoa.
		seal(first, postId, "DICA").andExpect(status().isOk());

		MvcResult batch = mockMvc.perform(get("/feed/posts/seals").param("ids", postId))
			.andExpect(status().isOk())
			.andReturn();
		var node = objectMapper.readTree(batch.getResponse().getContentAsString()).get(postId);
		assertThat(node.get("seals").valueStream().map(seal -> seal.get("seal").asText()).toList())
			.containsExactlyInAnyOrder("IMPORTANTE", "DICA");
		assertThat(node.get("seals").valueStream().mapToInt(seal -> seal.get("count").asInt()).sum()).isEqualTo(2);
		assertThat(node.get("mySeal").isNull()).isTrue(); // leitura sem sessao

		// Com sessao, o "meu selo" aparece — e clicar no mesmo remove.
		MvcResult mine = mockMvc.perform(get("/feed/posts/seals").param("ids", postId)
				.header("X-Session-Token", first.token()))
			.andExpect(status().isOk())
			.andReturn();
		assertThat(objectMapper.readTree(mine.getResponse().getContentAsString()).get(postId).get("mySeal").asText())
			.isEqualTo("DICA");

		seal(first, postId, "DICA").andExpect(status().isOk());
		MvcResult after = mockMvc.perform(get("/feed/posts/seals").param("ids", postId))
			.andExpect(status().isOk())
			.andReturn();
		assertThat(objectMapper.readTree(after.getResponse().getContentAsString()).get(postId).get("seals")
			.valueStream().mapToInt(seal -> seal.get("count").asInt()).sum()).isEqualTo(1);

		// Remover pelo DELETE tambem funciona (idempotente).
		mockMvc.perform(delete("/feed/posts/" + postId + "/seals").header("X-Session-Token", second.token()))
			.andExpect(status().isOk());
		mockMvc.perform(get("/feed/posts/seals").param("ids", postId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$." + postId + ".seals").isEmpty());
	}

	@Test
	void rejectsAnUnknownSeal() throws Exception {
		var author = auth.authenticate("seal_author2");
		String postId = publish(author, "Post com selo invalido");

		mockMvc.perform(post("/feed/posts/" + postId + "/seals")
				.header("X-Session-Token", author.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"seal\":\"NAO_EXISTE\"}"))
			.andExpect(status().isBadRequest());
	}

	private org.springframework.test.web.servlet.ResultActions seal(TestAuthHelper.AuthSession user, String postId,
			String seal) throws Exception {
		return mockMvc.perform(post("/feed/posts/" + postId + "/seals")
			.header("X-Session-Token", user.token())
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(Map.of("seal", seal))));
	}

	private String publish(TestAuthHelper.AuthSession author, String content) throws Exception {
		MvcResult result = mockMvc.perform(post("/feed/posts")
				.header("X-Session-Token", author.token())
				.header("Idempotency-Key", "seal-post-" + content.hashCode())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("type", "INSIGHT", "content", content, "tags", List.of(),
					"mediaIds", List.of()))))
			.andExpect(status().isCreated())
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
	}
}
