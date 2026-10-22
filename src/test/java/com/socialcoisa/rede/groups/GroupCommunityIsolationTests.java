package com.socialcoisa.rede.groups;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.socialcoisa.rede.shared.web.TestAuthHelper;

import tools.jackson.databind.ObjectMapper;

/**
 * Critério 11 da seção 21: isolamento entre Grupos e Comunidades.
 *
 * As duas features usam estruturas separadas (posts/comunidade × group_posts/grupo). Este teste
 * prova o caminho de leitura de cada uma com conteúdo marcado:
 * - o feed da comunidade NÃO traz a publicação do grupo;
 * - o feed do grupo NÃO traz a publicação da comunidade;
 * - cada um traz o próprio conteúdo (sem "vazio pelo motivo errado").
 */
@SpringBootTest
@AutoConfigureMockMvc
class GroupCommunityIsolationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	private TestAuthHelper authHelper() {
		return new TestAuthHelper(mockMvc, objectMapper);
	}

	private String createCommunity(TestAuthHelper.AuthSession dono, String name, String key) throws Exception {
		MvcResult result = mockMvc.perform(post("/communities")
				.header("X-Session-Token", dono.token())
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("name", name))))
			.andExpect(status().isCreated())
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
	}

	private String createGroup(TestAuthHelper.AuthSession dono, String name, String key) throws Exception {
		MvcResult result = mockMvc.perform(post("/groups")
				.header("X-Session-Token", dono.token())
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("name", name, "joinMode", "OPEN"))))
			.andExpect(status().isCreated())
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
	}

	private String topic(TestAuthHelper.AuthSession dono, String groupId, String key) throws Exception {
		MvcResult result = mockMvc.perform(post("/groups/" + groupId + "/topics")
				.header("X-Session-Token", dono.token())
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("name", "Geral"))))
			.andExpect(status().isCreated())
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
	}

	@Test
	void feedDaComunidadeENaoDoGrupoNaoSeMisturam() throws Exception {
		var dono = authHelper().authenticate("gci-dono");
		String communityId = createCommunity(dono, "Comunidade Isolada", "gci-community");
		String groupId = createGroup(dono, "Grupo Isolado", "gci-group");
		String topicId = topic(dono, groupId, "gci-topic");

		String marcadorComunidade = "marcador-que-pertence-a-comunidade";
		String marcadorGrupo = "marcador-que-pertence-ao-grupo";

		mockMvc.perform(post("/feed/posts")
				.header("X-Session-Token", dono.token())
				.header("Idempotency-Key", "gci-post-comunidade")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"type", "INSIGHT", "content", marcadorComunidade, "communityId", communityId))))
			.andExpect(status().isCreated());

		mockMvc.perform(post("/groups/" + groupId + "/posts")
				.header("X-Session-Token", dono.token())
				.header("Idempotency-Key", "gci-post-grupo")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"topicId", topicId, "content", marcadorGrupo))))
			.andExpect(status().isCreated());

		String communityFeed = mockMvc.perform(get("/communities/" + communityId + "/feed")
				.header("X-Session-Token", dono.token()))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		assertThat(communityFeed).contains(marcadorComunidade).doesNotContain(marcadorGrupo);

		String groupFeed = mockMvc.perform(get("/groups/" + groupId + "/posts")
				.header("X-Session-Token", dono.token()))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		assertThat(groupFeed).contains(marcadorGrupo).doesNotContain(marcadorComunidade);

		// O H2 dos testes é compartilhado entre classes: sem apagar, esta comunidade aparecia
		// na listagem global e quebrava o contrato de página vazia (ContractControllerTests).
		mockMvc.perform(delete("/communities/" + communityId)
				.header("X-Session-Token", dono.token())
				.header("Idempotency-Key", "gci-cleanup"))
			.andExpect(status().isOk());
	}
}
