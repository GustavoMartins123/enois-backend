package com.socialcoisa.rede.messaging;

import static org.assertj.core.api.Assertions.assertThat;
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
 * Reações em mensagens privadas: o histórico devolve o resumo por mensagem e a reação de quem
 * está vendo (destacada no front), com os mesmos tipos/emojis dos comentários.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MessageReactionViewTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void messageListCarriesReactionSummaryAndTheViewersOwnReaction() throws Exception {
		var auth = new TestAuthHelper(mockMvc, objectMapper);
		var alice = auth.authenticate("msg_reaction_alice");
		var bob = auth.authenticate("msg_reaction_bob");

		MvcResult conversation = mockMvc.perform(post("/conversations/direct")
				.header("X-Session-Token", alice.token())
				.header("Idempotency-Key", "msg-react-conv")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("userId", bob.userId()))))
			.andExpect(status().isOk())
			.andReturn();
		String conversationId = objectMapper.readTree(conversation.getResponse().getContentAsString())
			.get("id").asText();

		MvcResult sent = mockMvc.perform(post("/conversations/" + conversationId + "/messages")
				.header("X-Session-Token", alice.token())
				.header("Idempotency-Key", "msg-react-send")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("text", "oi, tudo bem?"))))
			.andExpect(status().isCreated())
			.andReturn();
		String messageId = objectMapper.readTree(sent.getResponse().getContentAsString()).get("id").asText();

		mockMvc.perform(post("/conversations/" + conversationId + "/messages/" + messageId + "/reactions")
				.header("X-Session-Token", bob.token())
				.header("Idempotency-Key", "msg-react-like")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("type", "LIKE"))))
			.andExpect(status().isOk());

		// Para quem reagiu: resumo com a contagem e a própria reação.
		String bobView = mockMvc.perform(get("/conversations/" + conversationId + "/messages")
				.header("X-Session-Token", bob.token()))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		var bobItem = objectMapper.readTree(bobView).get("items").get(0);
		assertThat(bobItem.get("reactions").get("LIKE").asInt()).isEqualTo(1);
		assertThat(bobItem.get("myReaction").asText()).isEqualTo("LIKE");

		// Para a outra pessoa: mesmo resumo, sem reação própria.
		String aliceView = mockMvc.perform(get("/conversations/" + conversationId + "/messages")
				.header("X-Session-Token", alice.token()))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		var aliceItem = objectMapper.readTree(aliceView).get("items").get(0);
		assertThat(aliceItem.get("reactions").get("LIKE").asInt()).isEqualTo(1);
		assertThat(aliceItem.get("myReaction").isNull()).isTrue();
	}
}
