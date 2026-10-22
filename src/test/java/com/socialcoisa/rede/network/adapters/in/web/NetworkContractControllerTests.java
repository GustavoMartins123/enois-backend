package com.socialcoisa.rede.network.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.ObjectMapper;
import com.socialcoisa.rede.shared.web.TestAuthHelper;

@SpringBootTest
@AutoConfigureMockMvc
class NetworkContractControllerTests {

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
	void suggestsRequestsAcceptsAndRemovesConnection() throws Exception {
		var sessionA = auth.authenticate("net_user_a_" + System.nanoTime());
		var sessionB = auth.authenticate("net_user_b_" + System.nanoTime());

		mockMvc.perform(get("/network/suggestions")
				.header("X-Session-Token", sessionA.token())
				.param("limit", "4"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray());

		mockMvc.perform(post("/network/connections/{target}", sessionB.userId())
				.header("X-Session-Token", sessionA.token())
				.header("Idempotency-Key", "net-req-" + sessionA.userId()))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.status").value("REQUESTED"));

		mockMvc.perform(get("/network/requests")
				.header("X-Session-Token", sessionB.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].fromUserId").value(sessionA.userId()));

		mockMvc.perform(put("/network/connections/{target}/accept", sessionA.userId())
				.header("X-Session-Token", sessionB.token())
				.header("Idempotency-Key", "net-acc-" + sessionA.userId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ACCEPTED"));

		mockMvc.perform(get("/network/summary")
				.header("X-Session-Token", sessionA.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.connections").value(1));

		mockMvc.perform(get("/network/summary")
				.header("X-Session-Token", sessionB.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.connections").value(1));

		mockMvc.perform(get("/network/suggestions")
				.header("X-Session-Token", sessionA.token())
				.param("limit", "4"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isArray());

		mockMvc.perform(get("/network/users")
				.header("X-Session-Token", sessionA.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].connected").value(true));

		mockMvc.perform(delete("/network/connections/{target}", sessionB.userId())
				.header("X-Session-Token", sessionA.token())
				.header("Idempotency-Key", "net-rem-" + sessionA.userId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("REMOVED"));

		mockMvc.perform(get("/network/summary")
				.header("X-Session-Token", sessionA.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.connections").value(0));
	}

	@Test
	void removedOrBlockedConnectionCanBeRequestedAgainAfterUnblocking() throws Exception {
		var a = auth.authenticate("net_reconnect_a_" + System.nanoTime());
		var b = auth.authenticate("net_reconnect_b_" + System.nanoTime());

		// Conecta e aceita.
		mockMvc.perform(post("/network/connections/{target}", b.userId())
				.header("X-Session-Token", a.token())
				.header("Idempotency-Key", "net-rc-connect"))
			.andExpect(status().isAccepted());
		mockMvc.perform(put("/network/connections/{target}/accept", a.userId())
				.header("X-Session-Token", b.token())
				.header("Idempotency-Key", "net-rc-accept"))
			.andExpect(status().isOk());

		// Remover (o "recusar" usa o mesmo endpoint) não impede a nova solicitação.
		mockMvc.perform(delete("/network/connections/{target}", b.userId())
				.header("X-Session-Token", a.token())
				.header("Idempotency-Key", "net-rc-remove"))
			.andExpect(status().isOk());
		mockMvc.perform(post("/network/connections/{target}", b.userId())
				.header("X-Session-Token", a.token())
				.header("Idempotency-Key", "net-rc-again"))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.status").value("REQUESTED"));

		// Aceita, bloqueia e confirma o 403 enquanto o bloqueio existir.
		mockMvc.perform(put("/network/connections/{target}/accept", a.userId())
				.header("X-Session-Token", b.token())
				.header("Idempotency-Key", "net-rc-accept-2"))
			.andExpect(status().isOk());
		mockMvc.perform(post("/network/blocks/{target}", b.userId())
				.header("X-Session-Token", a.token())
				.header("Idempotency-Key", "net-rc-block"))
			.andExpect(status().isCreated());
		mockMvc.perform(post("/network/connections/{target}", a.userId())
				.header("X-Session-Token", b.token())
				.header("Idempotency-Key", "net-rc-blocked"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("BLOCKED"));

		// Depois de desbloquear, a conexão volta a ser possível.
		mockMvc.perform(delete("/network/blocks/{target}", b.userId())
				.header("X-Session-Token", a.token())
				.header("Idempotency-Key", "net-rc-unblock"))
			.andExpect(status().isOk());
		mockMvc.perform(post("/network/connections/{target}", a.userId())
				.header("X-Session-Token", b.token())
				.header("Idempotency-Key", "net-rc-unblocked"))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.status").value("REQUESTED"));
	}

	@Test
	void replaysIdempotentConnectionRequest() throws Exception {
		var targetSession = auth.authenticate("net_idem_target_" + System.nanoTime());
		var session = auth.authenticate("net_idem_user");

		MvcResult first = mockMvc.perform(post("/network/connections/{target}", targetSession.userId())
				.header("X-Session-Token", session.token())
				.header("Idempotency-Key", "net-idem-req"))
			.andExpect(status().isAccepted())
			.andReturn();

		MvcResult replay = mockMvc.perform(post("/network/connections/{target}", targetSession.userId())
				.header("X-Session-Token", session.token())
				.header("Idempotency-Key", "net-idem-req"))
			.andExpect(status().isAccepted())
			.andReturn();

		assertThat(replay.getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());
	}
}
