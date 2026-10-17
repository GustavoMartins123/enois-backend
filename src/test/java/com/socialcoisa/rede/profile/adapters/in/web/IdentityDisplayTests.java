package com.socialcoisa.rede.profile.adapters.in.web;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import tools.jackson.databind.ObjectMapper;

import com.socialcoisa.rede.shared.web.TestAuthHelper;

/**
 * Identidade exibida: nome social substitui o de cadastro para todo mundo, apelido
 * entra entre parenteses, pronomes ficam so no perfil — e o nome de cadastro segue
 * existindo para o dono da conta e para a empresa da candidatura.
 */
@SpringBootTest
@AutoConfigureMockMvc
class IdentityDisplayTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	private TestAuthHelper auth;

	@BeforeEach
	void setUp() {
		auth = new TestAuthHelper(mockMvc, objectMapper);
	}

	private MockHttpServletRequestBuilder withSession(MockHttpServletRequestBuilder request,
			TestAuthHelper.AuthSession session) {
		return request.header("X-User-Id", session.userId()).header("X-Session-Token", session.token());
	}

	@Test
	void socialNameReplacesRegisteredNameForEveryoneAndNicknameAppends() throws Exception {
		var session = auth.authenticate("user_identity_a");

		mockMvc.perform(withSession(put("/me/identity"), session).contentType(MediaType.APPLICATION_JSON)
			.content("{\"nickname\":\"Bia\",\"socialName\":\"Beatriz Sol\",\"pronouns\":\"ela/dela\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Beatriz Sol (Bia)"))
			.andExpect(jsonPath("$.pronouns").value("ela/dela"));

		// Consulta publica: o nome de cadastro nao aparece para terceiros.
		var me = objectMapper
			.readTree(mockMvc.perform(withSession(get("/me"), session)).andReturn().getResponse()
				.getContentAsString());
		mockMvc.perform(get("/users/" + me.get("username").asText()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Beatriz Sol (Bia)"))
			.andExpect(jsonPath("$.pronouns").value("ela/dela"))
			// Nao existe campo de nome social na projecao publica (o nome de exibicao
			// ja e o social); o nome de cadastro nunca sai por aqui.
			.andExpect(jsonPath("$.socialName").doesNotExist());
	}

	@Test
	void clearingFieldsGoesBackToRegisteredName() throws Exception {
		var session = auth.authenticate("user_identity_b");

		mockMvc.perform(withSession(put("/me/identity"), session).contentType(MediaType.APPLICATION_JSON)
			.content("{\"nickname\":\"Bibi\",\"socialName\":\"B. Sol\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("B. Sol (Bibi)"));

		mockMvc.perform(withSession(put("/me/identity"), session).contentType(MediaType.APPLICATION_JSON)
			.content("{\"nickname\":null,\"socialName\":null,\"pronouns\":null}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("user_identity_b"))
			.andExpect(jsonPath("$.pronouns").doesNotExist());
	}

	@Test
	void ownerSeesEverythingOnMe() throws Exception {
		var session = auth.authenticate("user_identity_c");

		mockMvc.perform(withSession(put("/me/identity"), session).contentType(MediaType.APPLICATION_JSON)
			.content("{\"nickname\":\"Bi\",\"socialName\":\"B. Sol\",\"pronouns\":\"elu/delu\"}"))
			.andExpect(status().isOk());

		// O dono ve o proprio nome de cadastro (verificacao) E os campos de identidade.
		mockMvc.perform(withSession(get("/me"), session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("user_identity_c"))
			.andExpect(jsonPath("$.socialName").value("B. Sol"))
			.andExpect(jsonPath("$.nickname").value("Bi"))
			.andExpect(jsonPath("$.pronouns").value("elu/delu"));
	}

	@Test
	void refusesTooLongFields() throws Exception {
		var session = auth.authenticate("user_identity_d");

		mockMvc.perform(withSession(put("/me/identity"), session).contentType(MediaType.APPLICATION_JSON)
			.content("{\"nickname\":\"" + "a".repeat(61) + "\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("FIELD_TOO_LONG"));
	}

	@Test
	void requiresSession() throws Exception {
		mockMvc.perform(put("/me/identity").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void acceptsUpToFiveBadgesAndRefusesSix() throws Exception {
		var session = auth.authenticate("user_badge_a");

		String five = "{\"badges\":[\"https://cdn.enois.ia.br/b1.png\",\"https://cdn.enois.ia.br/b2.png\",\"https://cdn.enois.ia.br/b3.png\",\"https://cdn.enois.ia.br/b4.png\",\"https://cdn.enois.ia.br/b5.png\"]}";
		mockMvc.perform(withSession(put("/me/badges"), session).contentType(MediaType.APPLICATION_JSON)
			.content(five))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.badges.length()").value(5));

		String six = "{\"badges\":[\"https://cdn.enois.ia.br/b1.png\",\"https://cdn.enois.ia.br/b2.png\",\"https://cdn.enois.ia.br/b3.png\",\"https://cdn.enois.ia.br/b4.png\",\"https://cdn.enois.ia.br/b5.png\",\"https://cdn.enois.ia.br/b6.png\"]}";
		mockMvc.perform(withSession(put("/me/badges"), session).contentType(MediaType.APPLICATION_JSON)
			.content(six))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("MAX_BADGES"));
	}

	@Test
	void clearingBadgesIsEmptyList() throws Exception {
		var session = auth.authenticate("user_badge_b");

		mockMvc.perform(withSession(put("/me/badges"), session).contentType(MediaType.APPLICATION_JSON)
			.content("{\"badges\":[\"https://cdn.enois.ia.br/b1.png\"]}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.badges.length()").value(1));

		mockMvc.perform(withSession(put("/me/badges"), session).contentType(MediaType.APPLICATION_JSON)
			.content("{\"badges\":null}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.badges.length()").value(0));
	}

	@Test
	void theOldSingleBadgeFollowsTheList() throws Exception {
		var session = auth.authenticate("user_badge_c");

		// A coluna unica (o emblema antigo) espelha o primeiro da lista: e nela que as leituras de
		// autor caem quando a lista esta vazia.
		mockMvc.perform(withSession(put("/me/badges"), session).contentType(MediaType.APPLICATION_JSON)
			.content("{\"badges\":[\"https://cdn.enois.ia.br/b1.png\",\"https://cdn.enois.ia.br/b2.png\"]}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.badges.length()").value(2))
			.andExpect(jsonPath("$.badge").value("https://cdn.enois.ia.br/b1.png"));

		// Remover todos tem que remover de verdade: sem limpar a coluna antiga o emblema voltaria
		// a acompanhar o nome em todo o app depois de a pessoa tirar ele do perfil.
		mockMvc.perform(withSession(put("/me/badges"), session).contentType(MediaType.APPLICATION_JSON)
			.content("{\"badges\":[]}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.badges.length()").value(0))
			.andExpect(jsonPath("$.badge").doesNotExist());
	}
}
