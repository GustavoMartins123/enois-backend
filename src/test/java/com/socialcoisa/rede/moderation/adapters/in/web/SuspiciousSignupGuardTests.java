package com.socialcoisa.rede.moderation.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;

/**
 * Contenção imediata do ataque "probe": o cadastro com o padrão não nasce e o IP de origem é
 * fechado na mesma requisição (login e novos cadastros daquele endereço param).
 *
 * O portão fica desligado no perfil padrão da suíte (as contas de teste usam "probe..."); aqui
 * ele é ligado só neste contexto e a linha de blocked_ips é removida no fim.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SuspiciousSignupGuardTests {

	private static final String LOOPBACK = "127.0.0.1";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbc;

	@DynamicPropertySource
	static void props(DynamicPropertyRegistry registry) {
		registry.add("rede.security.signup-guard-enabled", () -> "true");
	}

	@Test
	void attackPatternSignupIsRefusedAndTheIpIsBlocked() throws Exception {
		jdbc.update("delete from blocked_ips where ip = ?", LOOPBACK);
		try {
			// Cadastro com o nome do ataque: 403 genérico (não revela a regra).
			mockMvc.perform(post("/auth/signup")
					.header("Idempotency-Key", "guard-signup-1")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(Map.of(
						"name", "Probe radica", "role", "USER", "email", "radica@test.com",
						"password", "Test1234!", "acceptedTerms", true, "birthDate", "1990-05-10"))))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("SIGNUP_REFUSED"));

			// O IP entrou na lista: o próximo login/cadastro daqui nem chega na senha.
			assertThat(jdbc.queryForList("select ip from blocked_ips where ip = ?", String.class, LOOPBACK))
				.contains(LOOPBACK);

			mockMvc.perform(post("/auth/login")
					.header("Idempotency-Key", "guard-login-1")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(Map.of(
						"email", "quem-ja-existe@test.com", "password", "Test1234!"))))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("IP_BLOCKED"));

			// Cadastro normal também é barrado pelo IP (o portão fechou a porta inteira).
			mockMvc.perform(post("/auth/signup")
					.header("Idempotency-Key", "guard-signup-2")
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(Map.of(
						"name", "Pessoa Normal", "role", "USER", "email", "normal@test.com",
						"password", "Test1234!", "acceptedTerms", true, "birthDate", "1990-05-10"))))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("IP_BLOCKED"));
		}
		finally {
			jdbc.update("delete from blocked_ips where ip = ?", LOOPBACK);
		}
	}
}
