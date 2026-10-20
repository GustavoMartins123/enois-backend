package com.socialcoisa.rede.messaging.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.socialcoisa.rede.shared.web.TestAuthHelper;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Mensagens diretas com mídia: ids anexados, texto opcional e teto de 4. */
@SpringBootTest
@AutoConfigureMockMvc
class MessageMediaTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void sendsAndReadsMediaIdsWithOptionalText() throws Exception {
		var auth = new TestAuthHelper(mockMvc, objectMapper);
		var a = auth.authenticate("dmm_a");
		var b = auth.authenticate("dmm_b");
		String conversationId = direct(a, b.userId());

		MvcResult sent = mockMvc.perform(post("/conversations/" + conversationId + "/messages")
				.header("X-Session-Token", a.token())
				.header("Idempotency-Key", "dmm-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"text", "olha isso",
					"mediaIds", List.of("media_1", "media_2")))))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.mediaIds.length()").value(2))
			.andReturn();
		String messageId = objectMapper.readTree(sent.getResponse().getContentAsString())
			.get("id").asText();

		mockMvc.perform(get("/conversations/" + conversationId + "/messages")
				.header("X-Session-Token", b.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[?(@.id == '" + messageId + "')].mediaIds.length()").value(2));

		// Sem texto, só mídia: continua válido.
		mockMvc.perform(post("/conversations/" + conversationId + "/messages")
				.header("X-Session-Token", a.token())
				.header("Idempotency-Key", "dmm-2")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"text", "", "mediaIds", List.of("media_3")))))
			.andExpect(status().isCreated());
	}

	@Test
	void rejectsEmptyMessageAndTooManyMedia() throws Exception {
		var auth = new TestAuthHelper(mockMvc, objectMapper);
		var a = auth.authenticate("dmm_c");
		var b = auth.authenticate("dmm_d");
		String conversationId = direct(a, b.userId());

		mockMvc.perform(post("/conversations/" + conversationId + "/messages")
				.header("X-Session-Token", a.token())
				.header("Idempotency-Key", "dmm-empty")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("text", "", "mediaIds", List.of()))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("EMPTY_MESSAGE"));

		mockMvc.perform(post("/conversations/" + conversationId + "/messages")
				.header("X-Session-Token", a.token())
				.header("Idempotency-Key", "dmm-many")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("text", "muitas",
					"mediaIds", List.of("m1", "m2", "m3", "m4", "m5")))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("TOO_MANY_MEDIA"));
	}

	private String direct(TestAuthHelper.AuthSession session, String targetUserId) throws Exception {
		MvcResult result = mockMvc.perform(post("/conversations/direct")
				.header("X-Session-Token", session.token())
				.header("Idempotency-Key", "dmm-conv-" + UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("userId", targetUserId))))
			.andExpect(status().isOk())
			.andReturn();
		JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
		assertThat(body.get("id").asText()).isNotBlank();
		return body.get("id").asText();
	}
}
