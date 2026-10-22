package com.socialcoisa.rede.shared.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.socialcoisa.rede.shared.web.TestAuthHelper;

import tools.jackson.databind.ObjectMapper;

/**
 * Teto de POST por usuário: com limite 1/min, a primeira ação passa e a segunda é 429 mesmo que a
 * sessão seja válida. Outro usuário continua livre; GET não é afetado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
	"rede.rate.post-guard-enabled=true",
	"rede.rate.post-user-per-minute=1",
	"rede.rate.post-ip-per-minute=1000"
})
class PostRateLimitTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void postIsLimitedPerUserButNotGet() throws Exception {
		var first = new TestAuthHelper(mockMvc, objectMapper).authenticate("prl_first");
		var second = new TestAuthHelper(mockMvc, objectMapper).authenticate("prl_second");

		mockMvc.perform(get("/auth/me").header("X-Session-Token", first.token()))
			.andExpect(status().isOk());

		mockMvc.perform(post("/auth/logout")
				.header("X-Session-Token", first.token())
				.header("Idempotency-Key", "prl-first"))
			.andExpect(status().isOk());

		mockMvc.perform(post("/auth/logout")
				.header("X-Session-Token", first.token())
				.header("Idempotency-Key", "prl-second"))
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("RATE_LIMITED"));

		// O balde é por sessão, não global.
		mockMvc.perform(post("/auth/logout")
				.header("X-Session-Token", second.token())
				.header("Idempotency-Key", "prl-other"))
			.andExpect(status().isOk());
	}
}
