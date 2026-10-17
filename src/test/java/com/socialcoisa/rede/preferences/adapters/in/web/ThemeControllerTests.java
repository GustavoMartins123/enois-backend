package com.socialcoisa.rede.preferences.adapters.in.web;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import tools.jackson.databind.ObjectMapper;

import com.socialcoisa.rede.shared.web.TestAuthHelper;

/**
 * O tema da conta: leitura, troca, limpeza e a recusa do que o backend nao conhece.
 * A escolha e individual e persistente — estes testes sao o contrato do par de
 * endpoints que a galeria de temas consome.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ThemeControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	private TestAuthHelper auth;

	@BeforeEach
	void setUp() {
		auth = new TestAuthHelper(mockMvc, objectMapper);
	}

	/** O contrato de autenticacao da casa: id da conta + token de sessao no cabecalho. */
	private MockHttpServletRequestBuilder withSession(MockHttpServletRequestBuilder request,
			TestAuthHelper.AuthSession session) {
		return request.header("X-User-Id", session.userId()).header("X-Session-Token", session.token());
	}

	@Test
	void withoutPreferenceReturnsNull() throws Exception {
		var session = auth.authenticate("user_theme_empty");

		mockMvc.perform(withSession(get("/me/theme"), session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.theme").value(nullValue()));
	}

	@Test
	void setValidThemePersistsIt() throws Exception {
		var session = auth.authenticate("user_theme_set");

		mockMvc.perform(withSession(put("/me/theme"), session).contentType(MediaType.APPLICATION_JSON)
			.content("{\"theme\":\"win95\"}"))
			.andExpect(status().isNoContent());

		mockMvc.perform(withSession(get("/me/theme"), session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.theme").value("win95"));

		// Troca de tema: o valor anterior e substituido, nao acumulado.
		mockMvc.perform(withSession(put("/me/theme"), session).contentType(MediaType.APPLICATION_JSON)
			.content("{\"theme\":\"synthwave\"}"))
			.andExpect(status().isNoContent());

		mockMvc.perform(withSession(get("/me/theme"), session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.theme").value("synthwave"));
	}

	@Test
	void nullThemeClearsThePreference() throws Exception {
		var session = auth.authenticate("user_theme_clear");

		mockMvc.perform(withSession(put("/me/theme"), session).contentType(MediaType.APPLICATION_JSON)
			.content("{\"theme\":\"terminal\"}"))
			.andExpect(status().isNoContent());

		mockMvc.perform(withSession(put("/me/theme"), session).contentType(MediaType.APPLICATION_JSON)
			.content("{\"theme\":null}"))
			.andExpect(status().isNoContent());

		mockMvc.perform(withSession(get("/me/theme"), session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.theme").value(nullValue()));
	}

	@Test
	void unknownThemeIsRefused() throws Exception {
		var session = auth.authenticate("user_theme_invalid");

		mockMvc.perform(withSession(put("/me/theme"), session).contentType(MediaType.APPLICATION_JSON)
			.content("{\"theme\":\"tema-inventado\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_THEME"));

		// A recusa nao grava nada: a preferencia continua inexistente.
		mockMvc.perform(withSession(get("/me/theme"), session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.theme").value(nullValue()));
	}

	@Test
	void requiresSession() throws Exception {
		mockMvc.perform(get("/me/theme")).andExpect(status().isUnauthorized());
		mockMvc.perform(put("/me/theme").contentType(MediaType.APPLICATION_JSON).content("{\"theme\":\"win95\"}"))
			.andExpect(status().isUnauthorized());
	}
}
