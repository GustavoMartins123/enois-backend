package com.socialcoisa.rede.preferences.adapters.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;
import com.socialcoisa.rede.shared.web.TestAuthHelper;

/**
 * Os três controles de personalização — fonte única no backend, com efeitos distintos.
 * Critérios da seção 10.1 e 10.3 do plano de extensão.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PersonalizationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	private TestAuthHelper auth;

	@BeforeEach
	void setUp() {
		auth = new TestAuthHelper(mockMvc, objectMapper);
	}

	@Test
	void defaultsAreEmptyListsAndPersist() throws Exception {
		var session = auth.authenticate("pers_defaults");

		mockMvc.perform(get("/me/preferences").header("X-Session-Token", session.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.likedTopics.length()").value(0))
			.andExpect(jsonPath("$.blockedTopics.length()").value(0))
			.andExpect(jsonPath("$.disabledModules.length()").value(0));

		mockMvc.perform(put("/me/preferences")
				.header("X-Session-Token", session.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(java.util.Map.of(
					"likedTopics", java.util.List.of("TECNOLOGIA"),
					"blockedTopics", java.util.List.of(),
					"disabledModules", java.util.List.of("DAILY_GAMES")))))
			.andDo(r -> System.out.println("PUT1 RESP " + r.getResponse().getStatus() + " " + r.getResponse().getContentAsString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.likedTopics[0]").value("TECNOLOGIA"))
			.andExpect(jsonPath("$.disabledModules[0]").value("DAILY_GAMES"));

		// Persistência entre "sessões": uma nova leitura devolve o mesmo estado.
		mockMvc.perform(get("/me/preferences").header("X-Session-Token", session.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.likedTopics[0]").value("TECNOLOGIA"))
			.andExpect(jsonPath("$.disabledModules[0]").value("DAILY_GAMES"));
	}

	@Test
	void invalidValuesAreRejected() throws Exception {
		var session = auth.authenticate("pers_invalid");

		mockMvc.perform(put("/me/preferences")
				.header("X-Session-Token", session.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(java.util.Map.of(
					"likedTopics", java.util.List.of("COSTURA")))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_ASSUNTO"));

		mockMvc.perform(put("/me/preferences")
				.header("X-Session-Token", session.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(java.util.Map.of(
					"disabledModules", java.util.List.of("CRÉDITO_IMOBILIÁRIO")))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_MÓDULO"));
	}

	@Test
	void likedAndBlockedCannotConflict() throws Exception {
		var session = auth.authenticate("pers_conflict");

		mockMvc.perform(put("/me/preferences")
				.header("X-Session-Token", session.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(java.util.Map.of(
					"likedTopics", java.util.List.of("DESIGN"),
					"blockedTopics", java.util.List.of("DESIGN")))))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("CONFLICTING_PREFERENCE"));
	}

	@Test
	void clearingPreferencesReturnsToDefault() throws Exception {
		var session = auth.authenticate("pers_clear");

		mockMvc.perform(put("/me/preferences")
				.header("X-Session-Token", session.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(java.util.Map.of(
					"likedTopics", java.util.List.of("TECNOLOGIA"),
					"blockedTopics", java.util.List.of("DESIGN"),
					"disabledModules", java.util.List.of("JOBS")))))
			.andExpect(status().isOk());

		// Volta ao padrão: listas vazias.
		mockMvc.perform(put("/me/preferences")
				.header("X-Session-Token", session.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(java.util.Map.of(
					"likedTopics", java.util.List.of(),
					"blockedTopics", java.util.List.of(),
					"disabledModules", java.util.List.of()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.likedTopics.length()").value(0))
			.andExpect(jsonPath("$.blockedTopics.length()").value(0))
			.andExpect(jsonPath("$.disabledModules.length()").value(0));
	}

	@Test
	void themeIsPreservedWhenPersonalizationChanges() throws Exception {
		var session = auth.authenticate("pers_theme");

		mockMvc.perform(put("/me/theme").header("X-Session-Token", session.token())
				.contentType(MediaType.APPLICATION_JSON).content("{\"theme\":\"terminal\"}"))
			.andExpect(status().isNoContent());

		mockMvc.perform(put("/me/preferences")
				.header("X-Session-Token", session.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(java.util.Map.of(
					"likedTopics", java.util.List.of("TECNOLOGIA")))))
			.andExpect(status().isOk());

		// O tema da conta continua na mesma linha das preferências.
		mockMvc.perform(get("/me/theme").header("X-Session-Token", session.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.theme").value("terminal"));
	}
}
