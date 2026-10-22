package com.socialcoisa.rede.identity.adapters.in.web;

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

import tools.jackson.databind.ObjectMapper;

import com.socialcoisa.rede.shared.web.TestAuthHelper;

/**
 * O throttle de credenciais (F-02/F-04 do pentest): 6 falhas de login seguidas na MESMA
 * conta → 429 LOGIN_THROTTLED (não 401, nem com a senha CERTA — o lock é por conta).
 */
@SpringBootTest(properties = {
	"rede.auth.throttle.login-max-failures=5",
	"rede.auth.throttle.signup-per-hour=100000"
})
@AutoConfigureMockMvc
class AuthThrottleTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void loginBruteForceNaMesmaContaFicaPreso() throws Exception {
		// A conta nasce com a senha certa (o helper cadastra + confirma).
		new TestAuthHelper(mockMvc, objectMapper).authenticate("throttle_vitima");

		// 5 falhas de senha na MESMA conta passam (401 cada).
		for (int i = 1; i <= 5; i++) {
			mockMvc.perform(post("/auth/login")
					.header("Idempotency-Key", "thr-login-fail-" + i)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(Map.of(
						"email", "throttle-vitima@test.com", "password", "senha-errada-" + i))))
				.andExpect(status().isUnauthorized());
		}

		// A 6ª já está travada: 429 LOGIN_THROTTLED — nem com a senha CERTA (o lock é por conta).
		// A senha do helper é a constante "Test1234!".
		mockMvc.perform(post("/auth/login")
				.header("Idempotency-Key", "thr-login-lock")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"email", "throttle-vitima@test.com", "password", "Test1234!"))))
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("LOGIN_THROTTLED"));
	}

	private TestAuthHelper.AuthSession auth(String name) throws Exception {
		return new TestAuthHelper(mockMvc, objectMapper).authenticate(name);
	}
}
