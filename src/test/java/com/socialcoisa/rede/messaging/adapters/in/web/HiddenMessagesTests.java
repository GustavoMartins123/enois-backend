package com.socialcoisa.rede.messaging.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
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

/**
 * "Apagar para mim" por MENSAGEM (o da conversa inteira já existia).
 *
 * O que este teste protege: a mensagem some só de quem apagou — para o outro participante ela
 * continua na conversa (é o que separa "apagar para mim" de "apagar para todos", que aqui não
 * existe), e o filtro acontece no banco, então a paginação não abre buraco.
 */
@SpringBootTest
@AutoConfigureMockMvc
class HiddenMessagesTests {

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
	void hidingAMessageOnlyAffectsWhoAsked() throws Exception {
		var a = auth.authenticate("hide_msg_a");
		var b = auth.authenticate("hide_msg_b");
		String conversationId = createConversation(a, b.userId());
		String messageId = send(a, conversationId, "combinado, te mando o orçamento amanhã");

		mockMvc.perform(post("/conversations/" + conversationId + "/messages/hide")
				.header("X-Session-Token", a.token())
				.header("Idempotency-Key", "hide-msg-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("ids", List.of(messageId)))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.hidden").value(1));

		// A nao ve mais; B continua vendo a mesma mensagem.
		assertThat(messageIds(a, conversationId)).doesNotContain(messageId);
		assertThat(messageIds(b, conversationId)).contains(messageId);

		// Apagar de novo e idempotente (nao cria linha repetida nem estoura a unique).
		mockMvc.perform(post("/conversations/" + conversationId + "/messages/hide")
				.header("X-Session-Token", a.token())
				.header("Idempotency-Key", "hide-msg-2")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("ids", List.of(messageId)))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.hidden").value(0));
	}

	@Test
	void refusesHidingInAConversationThePersonIsNotIn() throws Exception {
		var a = auth.authenticate("hide_msg_c");
		var b = auth.authenticate("hide_msg_d");
		var outsider = auth.authenticate("hide_msg_e");
		String conversationId = createConversation(a, b.userId());
		String messageId = send(a, conversationId, "oi");

		mockMvc.perform(post("/conversations/" + conversationId + "/messages/hide")
				.header("X-Session-Token", outsider.token())
				.header("Idempotency-Key", "hide-msg-outsider")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("ids", List.of(messageId)))))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("NOT_PARTICIPANT"));
	}

	@Test
	void emptySelectionIsRejected() throws Exception {
		var a = auth.authenticate("hide_msg_f");
		var b = auth.authenticate("hide_msg_g");
		String conversationId = createConversation(a, b.userId());

		mockMvc.perform(post("/conversations/" + conversationId + "/messages/hide")
				.header("X-Session-Token", a.token())
				.header("Idempotency-Key", "hide-msg-empty")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("ids", List.of()))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("EMPTY_MESSAGE_LIST"));
	}

	private List<String> messageIds(TestAuthHelper.AuthSession session, String conversationId) throws Exception {
		MvcResult result = mockMvc
			.perform(get("/conversations/" + conversationId + "/messages").header("X-Session-Token", session.token()))
			.andExpect(status().isOk()).andReturn();
		JsonNode items = objectMapper.readTree(result.getResponse().getContentAsString()).get("items");
		List<String> out = new ArrayList<>();
		for (int i = 0; i < items.size(); i++) out.add(items.get(i).get("id").asText());
		return out;
	}

	private String send(TestAuthHelper.AuthSession session, String conversationId, String text) throws Exception {
		MvcResult result = mockMvc.perform(post("/conversations/" + conversationId + "/messages")
				.header("X-Session-Token", session.token())
				.header("Idempotency-Key", "hide-msg-send-" + session.userId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("text", text))))
			.andExpect(status().isCreated()).andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
	}

	private String createConversation(TestAuthHelper.AuthSession a, String otherId) throws Exception {
		MvcResult result = mockMvc.perform(post("/conversations")
				.header("X-Session-Token", a.token())
				.header("Idempotency-Key", "hide-msg-conv-" + a.userId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("kind", "DIRECT", "participantIds", List.of(otherId)))))
			.andExpect(status().isCreated()).andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
	}
}
