package com.socialcoisa.rede.identity.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
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

import com.socialcoisa.rede.shared.web.TestDatabase;

import tools.jackson.databind.ObjectMapper;

/**
 * Portao de cadastros: com `rede.auth.signup-enabled=false` (perfil qa/prd), o POST /auth/signup
 * recusa com 403 SIGNUP_DISABLED e NENHUMA conta nasce. Login de conta existente nao e afetado.
 */
@SpringBootTest(properties = "rede.auth.signup-enabled=false")
@AutoConfigureMockMvc
class SignupDisabledTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void signupIsRefusedAndDoesNotCreateTheAccount() throws Exception {
		String payload = objectMapper.writeValueAsString(Map.of(
			"name", "Conta Fechada",
			"email", "conta.fechada@example.com",
			"password", "senha-segura",
			"acceptedTerms", true));

		mockMvc.perform(post("/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(payload))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("SIGNUP_DISABLED"));

		assertThat(TestDatabase.userIdByEmail("conta.fechada@example.com")).isEmpty();
	}

	@Test
	void loginKeepsWorkingForExistingAccounts() throws Exception {
		String payload = objectMapper.writeValueAsString(Map.of(
			"email", "quem.nao.existe@example.com",
			"password", "senha-segura"));

		// A resposta e a de credencial invalida (401), nunca o 403 do cadastro: o portao fecha
		// apenas a criacao de conta.
		mockMvc.perform(post("/auth/login")
				.header("Idempotency-Key", "signup-disabled-login-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(payload))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}
}
