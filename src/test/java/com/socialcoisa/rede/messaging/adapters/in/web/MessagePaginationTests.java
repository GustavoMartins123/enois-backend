package com.socialcoisa.rede.messaging.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.socialcoisa.rede.shared.web.TestAuthHelper;

/**
 * Mensagens antigas precisam ser alcancaveis: a listagem tem que paginar por cursor em vez de
 * devolver sempre as mais recentes.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MessagePaginationTests {

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
	void listingOlderMessagesUsesCursor() throws Exception {
		var a = auth.authenticate("msg_page_a");
		var b = auth.authenticate("msg_page_b");
		String conversationId = createConversation(a, b.userId());

		// 5 mensagens: 1..5.
		for (int i = 1; i <= 5; i++) {
			send(a, conversationId, "mensagem " + i, "mp-msg-" + i);
		}

		// Primeira pagina (2 mais recentes) traz cursor para as antigas.
		JsonNode first = list(a, conversationId, "limit=2", null);
		assertThat(first.get("items").size()).isEqualTo(2);
		assertThat(first.get("nextCursor").isNull()).isFalse();

		// Segunda pagina a partir do cursor: mensagens mais antigas.
		String cursor = first.get("nextCursor").asText();
		JsonNode second = list(a, conversationId, "limit=2", cursor);
		assertThat(second.get("items").size()).isEqualTo(2);

		java.util.Set<String> firstPage = texts(first);
		java.util.Set<String> secondPage = texts(second);
		assertThat(firstPage).doesNotContainAnyElementsOf(secondPage);
	}

	@Test
	void lastPageHasNoCursor() throws Exception {
		var a = auth.authenticate("msg_page_a2");
		var b = auth.authenticate("msg_page_b2");
		String conversationId = createConversation(a, b.userId());
		send(a, conversationId, "unica", "mp-only-1");

		JsonNode page = list(a, conversationId, "limit=20", null);
		assertThat(page.get("items").size()).isEqualTo(1);
		assertThat(page.get("nextCursor").isNull()).isTrue();
	}

	private java.util.Set<String> texts(JsonNode page) {
		java.util.Set<String> result = new java.util.HashSet<>();
		for (int i = 0; i < page.get("items").size(); i++) {
			result.add(page.get("items").get(i).get("text").asText());
		}
		return result;
	}

	private String createConversation(TestAuthHelper.AuthSession a, String otherId) throws Exception {
		MvcResult result = mockMvc.perform(post("/conversations")
				.header("X-Session-Token", a.token())
				.header("Idempotency-Key", "mp-conv-" + a.userId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("kind", "DIRECT", "participantIds", List.of(otherId)))))
			.andExpect(status().isCreated())
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
	}

	private void send(TestAuthHelper.AuthSession session, String conversationId, String text, String idem)
			throws Exception {
		mockMvc.perform(post("/conversations/" + conversationId + "/messages")
				.header("X-Session-Token", session.token())
				.header("Idempotency-Key", idem)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("text", text))))
			.andExpect(status().isCreated());
	}

	private JsonNode list(TestAuthHelper.AuthSession session, String conversationId, String limitParam, String cursor)
			throws Exception {
		String query = limitParam + (cursor != null ? "&cursor=" + cursor : "");
		MvcResult result = mockMvc.perform(get("/conversations/" + conversationId + "/messages?" + query)
				.header("X-Session-Token", session.token()))
			.andExpect(status().isOk())
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}
}