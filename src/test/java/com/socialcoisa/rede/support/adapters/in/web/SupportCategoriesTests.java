package com.socialcoisa.rede.support.adapters.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import tools.jackson.databind.ObjectMapper;

/** Categoria e status dos tickets + log de metadados de e-mail no painel central. */
@SpringBootTest
@AutoConfigureMockMvc
class SupportCategoriesTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@DynamicPropertySource
	static void admin(DynamicPropertyRegistry registry) {
		registry.add("rede.admin.emails", () -> "sc_admin@test.com");
	}

	@Test
	void adminFiltersByCategoryAndMovesTheTicketStatus() throws Exception {
		var user = new TestAuthHelper(mockMvc, objectMapper).authenticate("sc_user");
		var admin = new TestAuthHelper(mockMvc, objectMapper).authenticate("sc_admin");

		MvcResult created = mockMvc.perform(post("/support/requests")
				.header("X-Session-Token", user.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"subject", "O botão de publicar some no celular",
					"message", "No iPhone o botão fica atrás da barra inferior e não consigo publicar.",
					"contactEmail", "sc_user@test.com",
					"category", "BUG"))))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.category").value("BUG"))
			.andExpect(jsonPath("$.status").value("OPEN"))
			.andReturn();
		String ticketId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

		// Não-admin não enxerga a fila nem o log.
		mockMvc.perform(get("/admin/support").header("X-Session-Token", user.token()))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/admin/emails").header("X-Session-Token", user.token()))
			.andExpect(status().isForbidden());

		// Filtro por categoria: o ticket aparece em BUG/OPEN.
		mockMvc.perform(get("/admin/support").param("category", "BUG").param("status", "OPEN")
				.header("X-Session-Token", admin.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.id == '" + ticketId + "')].category").value("BUG"));

		// Outra categoria não o traz.
		var other = mockMvc.perform(get("/admin/support").param("category", "IDEA")
				.header("X-Session-Token", admin.token()))
			.andExpect(status().isOk()).andReturn();
		org.assertj.core.api.Assertions.assertThat(other.getResponse().getContentAsString())
			.doesNotContain(ticketId);

		// Triagem: em andamento.
		mockMvc.perform(patch("/admin/support/" + ticketId)
				.header("X-Session-Token", admin.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("status", "IN_PROGRESS"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("IN_PROGRESS"));

		mockMvc.perform(get("/admin/support").param("status", "IN_PROGRESS")
				.header("X-Session-Token", admin.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.id == '" + ticketId + "')].status").value("IN_PROGRESS"));

		// O cadastro tenta confirmar e-mail (SMTP ausente no teste) e isso fica no log de metadados.
		mockMvc.perform(get("/admin/emails").header("X-Session-Token", admin.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.toEmail == 'sc_user@test.com')].status").value("FAILED"));
	}
}
