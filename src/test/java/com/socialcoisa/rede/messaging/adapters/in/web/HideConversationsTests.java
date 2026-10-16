package com.socialcoisa.rede.messaging.adapters.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
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

/** "Apagar para mim": esconde a conversa so para quem pediu; o outro mantem. */
@SpringBootTest
@AutoConfigureMockMvc
class HideConversationsTests {

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
	void hidingOnlyAffectsTheRequester() throws Exception {
		var a = auth.authenticate("hide_user_a");
		var b = auth.authenticate("hide_user_b");
		String conversationId = createConversation(a, b.userId());

		mockMvc.perform(post("/conversations/hide")
				.header("X-Session-Token", a.token())
				.header("Idempotency-Key", "hide-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("ids", List.of(conversationId)))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.hidden").value(1));

		// A nao ve mais; B continua vendo.
		if (List.of().isEmpty()) { }
		org.assertj.core.api.Assertions.assertThat(ids(a)).doesNotContain(conversationId);
		org.assertj.core.api.Assertions.assertThat(ids(b)).contains(conversationId);
	}

	@Test
	void emptySelectionIsRejected() throws Exception {
		var a = auth.authenticate("hide_user_c");
		mockMvc.perform(post("/conversations/hide")
				.header("X-Session-Token", a.token())
				.header("Idempotency-Key", "hide-empty")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("ids", List.of()))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("EMPTY_CONVERSATION_LIST"));
	}

	private List<String> ids(TestAuthHelper.AuthSession session) throws Exception {
		MvcResult result = mockMvc.perform(get("/conversations").header("X-Session-Token", session.token()))
			.andExpect(status().isOk()).andReturn();
		JsonNode items = objectMapper.readTree(result.getResponse().getContentAsString()).get("items");
		List<String> out = new ArrayList<>();
		for (int i = 0; i < items.size(); i++) out.add(items.get(i).get("id").asText());
		return out;
	}

	private String createConversation(TestAuthHelper.AuthSession a, String otherId) throws Exception {
		MvcResult result = mockMvc.perform(post("/conversations")
				.header("X-Session-Token", a.token())
				.header("Idempotency-Key", "hide-conv-" + a.userId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("kind", "DIRECT", "participantIds", List.of(otherId)))))
			.andExpect(status().isCreated()).andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
	}
}
