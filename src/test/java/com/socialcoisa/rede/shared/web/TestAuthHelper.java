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

	public record AuthSession(String token, String userId) {}

	private final MockMvc mockMvc;
	private final ObjectMapper objectMapper;

	public TestAuthHelper(MockMvc mockMvc, ObjectMapper objectMapper) {
		this.mockMvc = mockMvc;
		this.objectMapper = objectMapper;
	}

	/**
	 * Cria a conta e devolve uma sessao valida.
	 *
	 * O cadastro nasce pendente de confirmacao de e-mail e so emite sessao depois do link. Como o
	 * token so existe no e-mail enviado, aqui confirmamos o e-mail direto no banco e entramos pelo
	 * login normal; o fluxo real de confirmacao tem teste proprio (EmailVerificationFlowTests).
	 */
	public AuthSession authenticate(String userId) throws Exception {
		String email = userId + "@test.com";
		String payload = objectMapper.writeValueAsString(Map.of(
			"name", userId,
			"role", "USER",
			"email", email,
			"password", "Test1234!",
			"acceptedTerms", true
		));

		mockMvc.perform(post("/auth/signup")
				.header("Idempotency-Key", "signup-" + userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(payload))
			.andExpect(status().isCreated());

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
		return new AuthSession(node.get("token").asText(), createdUserId);
	}
}
