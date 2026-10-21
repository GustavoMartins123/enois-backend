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
	 * Se a conta JA existe (o cadastro e um replay da idempotencia quando o mesmo teste pede a
	 * mesma conta duas vezes), nao ha pendente para confirmar: entra-se pelo login normal.
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

			MvcResult verified = mockMvc.perform(post("/auth/email/verify")
					.header("Idempotency-Key", "verify-" + userId)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(Map.of("token", confirmToken))))
				.andExpect(status().isOk())
				.andReturn();

			String createdUserId = TestDatabase.userIdByEmail(email)
				.orElseThrow(() -> new IllegalStateException("usuario nao criado: " + email));
			JsonNode node = objectMapper.readTree(verified.getResponse().getContentAsString());
			return new AuthSession(node.get("token").asText(), node.get("refreshToken").asText(), createdUserId);
		}

		// A conta ja existia (replay do cadastro): entra pelo login normal.
		MvcResult login = mockMvc.perform(post("/auth/login")
				.header("Idempotency-Key", "login-" + userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("email", email, "password", "Test1234!"))))
			.andExpect(status().isOk())
			.andReturn();

		String createdUserId = TestDatabase.userIdByEmail(email)
			.orElseThrow(() -> new IllegalStateException("usuario nao criado: " + email));
		JsonNode node = objectMapper.readTree(login.getResponse().getContentAsString());
		return new AuthSession(node.get("token").asText(), node.get("refreshToken").asText(), createdUserId);
	}
}
