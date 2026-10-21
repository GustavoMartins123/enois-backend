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
	 * O cadastro e em duas etapas desde 2026-10-21: o POST /auth/signup so guarda o pendente e a
	 * conta nasce na confirmacao do e-mail. Como o token cru so existe no e-mail, aqui trocamos o
	 * hash do pendente por um valor conhecido (TestDatabase) e chamamos o MESMO endpoint de
	 * verificacao que o link usaria — a conta nasce verificada e a resposta ja traz a sessao.
	 */
	public AuthSession authenticate(String userId) throws Exception {
		String email = userId + "@test.com";
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

		String rawToken = "verify-" + userId;
		TestDatabase.setPendingSignupToken(email, rawToken);

		MvcResult verified = mockMvc.perform(post("/auth/email/verify")
				.header("Idempotency-Key", "verify-" + userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("token", rawToken))))
			.andExpect(status().isOk())
			.andReturn();

		String createdUserId = TestDatabase.userIdByEmail(email)
			.orElseThrow(() -> new IllegalStateException("usuario nao criado: " + email));
		JsonNode node = objectMapper.readTree(verified.getResponse().getContentAsString());
		return new AuthSession(node.get("token").asText(), node.get("refreshToken").asText(), createdUserId);
	}
}
