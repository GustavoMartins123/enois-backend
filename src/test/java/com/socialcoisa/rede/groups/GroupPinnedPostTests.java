package com.socialcoisa.rede.groups;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

import com.socialcoisa.rede.shared.web.TestAuthHelper;

import tools.jackson.databind.ObjectMapper;

/**
 * Publicação fixada no topo do grupo: no máximo UMA, só dono/admin fixa (PIN_CONTENT), o membro
 * comum recebe 403, e desafixar libera o topo.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GroupPinnedPostTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	private TestAuthHelper authHelper() {
		return new TestAuthHelper(mockMvc, objectMapper);
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

	private String publish(TestAuthHelper.AuthSession autor, String groupId, String topicId, String text, String key)
			throws Exception {
		MvcResult result = mockMvc.perform(post("/groups/" + groupId + "/posts")
				.header("X-Session-Token", autor.token())
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("topicId", topicId, "content", text))))
			.andExpect(status().isCreated())
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
	}

	@Test
	void fixaUmSoESoDonoOuAdmin() throws Exception {
		var dono = authHelper().authenticate("gpin-dono");
		var membro = authHelper().authenticate("gpin-membro");
		String groupId = createGroup(dono, "Grupo Pin", "gpin-create");
		String topicId = topic(dono, groupId, "gpin-topic");
		String postA = publish(dono, groupId, topicId, "primeiro", "gpin-post-a");
		String postB = publish(dono, groupId, topicId, "segundo", "gpin-post-b");

		mockMvc.perform(post("/groups/" + groupId + "/join")
				.header("X-Session-Token", membro.token())
				.header("Idempotency-Key", "gpin-join"))
			.andExpect(status().isOk());

		// Membro comum não fixa.
		mockMvc.perform(post("/groups/" + groupId + "/posts/" + postA + "/pin")
				.header("X-Session-Token", membro.token())
				.header("Idempotency-Key", "gpin-member-pin"))
			.andExpect(status().isForbidden());

		// O dono fixa A.
		mockMvc.perform(post("/groups/" + groupId + "/posts/" + postA + "/pin")
				.header("X-Session-Token", dono.token())
				.header("Idempotency-Key", "gpin-pin-a"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(postA))
			.andExpect(jsonPath("$.pinned").value(true));

		mockMvc.perform(get("/groups/" + groupId + "/pinned").header("X-Session-Token", dono.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(postA));

		// Fixar B substitui A: a listagem tem exatamente um pinned, e é B.
		mockMvc.perform(post("/groups/" + groupId + "/posts/" + postB + "/pin")
				.header("X-Session-Token", dono.token())
				.header("Idempotency-Key", "gpin-pin-b"))
			.andExpect(status().isOk());

		mockMvc.perform(get("/groups/" + groupId + "/pinned").header("X-Session-Token", dono.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(postB));

		MvcResult lista = mockMvc.perform(get("/groups/" + groupId + "/posts")
				.header("X-Session-Token", dono.token()))
			.andExpect(status().isOk())
			.andReturn();
		var items = objectMapper.readTree(lista.getResponse().getContentAsString()).get("items");
		long pinned = 0;
		for (var item : items) {
			if (item.get("pinned").asBoolean()) {
				pinned++;
				assertThat(item.get("id").asText()).isEqualTo(postB);
			}
		}
		assertThat(pinned).isEqualTo(1);

		// Desafixar libera o topo.
		mockMvc.perform(delete("/groups/" + groupId + "/posts/" + postB + "/pin")
				.header("X-Session-Token", dono.token())
				.header("Idempotency-Key", "gpin-unpin"))
			.andExpect(status().isOk());

		mockMvc.perform(get("/groups/" + groupId + "/pinned").header("X-Session-Token", dono.token()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NO_PINNED_POST"));
	}
}
