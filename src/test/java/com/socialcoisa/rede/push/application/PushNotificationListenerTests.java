package com.socialcoisa.rede.push.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.socialcoisa.rede.push.application.port.out.PushSender;
import com.socialcoisa.rede.shared.web.TestAuthHelper;

import tools.jackson.databind.ObjectMapper;

/**
 * Garante que toda notificacao criada dentro do app tambem vira push no celular: a criacao roda
 * dentro de uma transacao e o envio precisa acontecer depois do commit, sem quebrar o fluxo.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PushNotificationListenerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private PushSender pushSender;

	private TestAuthHelper auth;

	@BeforeEach
	void setUp() {
		auth = new TestAuthHelper(mockMvc, objectMapper);
	}

	@Test
	void connectionRequestPushesToTheReceiver() throws Exception {
		var requester = auth.authenticate("push_requester");
		var receiver = auth.authenticate("push_receiver");

		mockMvc.perform(post("/network/connections/" + receiver.userId())
				.header("X-Session-Token", requester.token())
				.header("Idempotency-Key", "push-conn-1"))
			.andExpect(status().isAccepted());

		verify(pushSender, timeout(5000)).sendToUser(eq(receiver.userId()), any(PushSender.PushMessage.class));
	}
}