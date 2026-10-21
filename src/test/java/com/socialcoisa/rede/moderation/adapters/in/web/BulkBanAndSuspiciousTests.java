package com.socialcoisa.rede.moderation.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.socialcoisa.rede.shared.web.TestAuthHelper;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Banimento em lote e triagem de contas suspeitas: os dois caminhos que a limpeza de cadastros
 * "probe" usa. O lote precisa ser parcial (um username inválido não derruba os outros), idempotente
 * (banir de novo não cria outra sanção) e revogar as sessões de quem foi banido.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BulkBanAndSuspiciousTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@DynamicPropertySource
	static void props(DynamicPropertyRegistry registry) {
		registry.add("rede.admin.emails", () -> "bb_admin@test.com");
	}

	@Test
	void bulkBanReportsEachUserAndSuspiciousListFlagsProbeNames() throws Exception {
		var admin = new TestAuthHelper(mockMvc, objectMapper).authenticate("bb_admin");
		var first = new TestAuthHelper(mockMvc, objectMapper).authenticate("probe_bulk_a");
		var second = new TestAuthHelper(mockMvc, objectMapper).authenticate("probe_bulk_b");

		MvcResult suspicious = mockMvc.perform(get("/admin/users/suspicious")
				.header("X-Session-Token", admin.token())
				.param("days", "1")
				.param("limit", "200"))
			.andExpect(status().isOk())
			.andReturn();
		JsonNode list = objectMapper.readTree(suspicious.getResponse().getContentAsString());
		assertThat(list.get("items").toString()).contains("probe-bulk-a", "probe-bulk-b");
		assertThat(list.get("items").toString()).contains("NOME_SUSPEITO");

		// Lista é só de admin.
		mockMvc.perform(get("/admin/users/suspicious").header("X-Session-Token", first.token()))
			.andExpect(status().isForbidden());

		MvcResult banned = mockMvc.perform(post("/admin/users/bulk-ban")
				.header("X-Session-Token", admin.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"usernames", List.of("probe-bulk-a", "probe-bulk-b", "bb-admin", "nao-existe"),
					"type", "BAN",
					"reason", "Cadastros em série criados para sondar a plataforma."))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(4))
			.andExpect(jsonPath("$.banned").value(2))
			.andExpect(jsonPath("$.failed").value(2))
			.andReturn();
		assertThat(objectMapper.readTree(banned.getResponse().getContentAsString()).get("items").toString())
			.contains("bb-admin", "nao-existe", "FAILED");

		// Sessão revogada e login novo bloqueado.
		mockMvc.perform(get("/auth/me").header("X-Session-Token", first.token()))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/auth/login")
				.header("Idempotency-Key", "bulk-login-banned")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"email", "probe_bulk_a@test.com", "password", "Test1234!"))))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ACCOUNT_BANNED"));

		// Repetir o lote é idempotente: não cria uma segunda sanção ativa.
		mockMvc.perform(post("/admin/users/bulk-ban")
				.header("X-Session-Token", admin.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"usernames", List.of("probe-bulk-a", "probe-bulk-b"),
					"type", "BAN",
					"reason", "Cadastros em série criados para sondar a plataforma."))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.banned").value(0))
			.andExpect(jsonPath("$.alreadyBanned").value(2));
		assertThat(second.token()).isNotBlank();
	}
}
