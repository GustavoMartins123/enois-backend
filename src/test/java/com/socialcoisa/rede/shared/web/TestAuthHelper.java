package com.socialcoisa.rede.shared.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public class TestAuthHelper {

	public record AuthSession(String token, String refreshToken, String userId) {}

	private final MockMvc mockMvc;
	private final ObjectMapper objectMapper;

	public TestAuthHelper(MockMvc mockMvc, ObjectMapper objectMapper) {
		this.mockMvc = mockMvc;
		this.objectMapper = objectMapper;
	}

	/**
	 * Cria a conta e devolve uma sessao valida.
	 *
	 * O cadastro é em DUAS etapas (2026-10-21): o POST /auth/signup só guarda um cadastro
	 * pendente e manda o link por e-mail; a conta nasce quando o link é usado. Como o token cru
	 * só existe no e-mail (que não sai no teste), aqui escrevemos um token conhecido no
	 * cadastro pendente, confirmamos o e-mail pelo endpoint real e entramos pelo login normal.
	 * O fluxo de verdade tem teste proprio (PendingSignupFlowTests).
	 */
	public AuthSession authenticate(String userId) throws Exception {
		String email = userId + "@test.com";

		// Idempotente: a mesma conta pode ser pedida por varios testes da mesma classe. O
		// cadastro e um replay da idempotencia (nao nasce outro pendente), entao a conta que
		// ja existe e usada como esta.
		if (TestDatabase.userIdByEmail(email).isEmpty()) {
			String payload = objectMapper.writeValueAsString(Map.of(
				"name", userId,
				"role", "USER",
				"email", email,
				"password", "Test1234!",
				"acceptedTerms", true,
				"birthDate", "1990-05-10"
			));

			mockMvc.perform(post("/auth/signup")
					.header("Idempotency-Key", "signup-" + userId)
					.contentType(MediaType.APPLICATION_JSON)
					.content(payload))
				.andExpect(status().isCreated());

			// Token conhecido no cadastro pendente: o e-mail nao sai no teste, mas o hash do
			// token fica no banco — e e por ele que o endpoint de confirmacao casa o link.
			String confirmToken = "token-teste-" + userId;
			TestDatabase.setPendingSignupToken(email, confirmToken);

			mockMvc.perform(post("/auth/email/verify")
					.header("Idempotency-Key", "verify-" + userId)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(Map.of("token", confirmToken))))
				.andExpect(status().isOk());
		}

		String createdUserId = TestDatabase.userIdByEmail(email)
			.orElseThrow(() -> new IllegalStateException("usuario nao criado: " + email));
		TestDatabase.markEmailVerified(createdUserId, java.time.Instant.now());

		MvcResult login = mockMvc.perform(post("/auth/login")
				.header("Idempotency-Key", "login-" + userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("email", email, "password", "Test1234!"))))
			.andExpect(status().isOk())
			.andReturn();

		JsonNode node = objectMapper.readTree(login.getResponse().getContentAsString());
		return new AuthSession(node.get("token").asText(), node.get("refreshToken").asText(), createdUserId);
	}
}
