package com.socialcoisa.rede.apiv1.origins;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.socialcoisa.rede.shared.web.TestAuthHelper;

import tools.jackson.databind.ObjectMapper;

/**
 * Gate de origem em TODAS as rotas: navegador manda Origin, servidor manda X-Client-Origin.
 * A origem precisa estar na allowlist do app ou cadastrada em /developers/origins.
 *
 * O resto da suíte roda com `rede.origins.enforce=false` (ver src/test/resources/application.properties),
 * porque o MockMvc não simula um navegador; aqui a enforcement é ligada explicitamente.
 */
@SpringBootTest(properties = "rede.origins.enforce=true")
@AutoConfigureMockMvc
class DeveloperOriginTests {

	private static final String APP_ORIGIN = "https://enois.ia.br";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void everyNonExemptRequestNeedsARegisteredOrigin() throws Exception {
		var user = new TestAuthHelper(mockMvc, objectMapper).authenticate("origin_user");

		// Sem Origin e sem X-Client-Origin: recusado antes de chegar ao controller.
		mockMvc.perform(get("/developers/origins").header("X-Session-Token", user.token()))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ORIGIN_REQUIRED"));

		// Servidor (SSR/curl) declara a origem do próprio app.
		mockMvc.perform(get("/developers/origins")
				.header("X-Session-Token", user.token())
				.header("X-Client-Origin", APP_ORIGIN))
			.andExpect(status().isOk());

		// Origem não cadastrada, em qualquer rota, é recusada.
		mockMvc.perform(get("/developers/origins")
				.header("X-Session-Token", user.token())
				.header("Origin", "https://evil.example"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ORIGIN_NOT_ALLOWED"));

		// Um X-Client-Origin forjado NÃO substitui um Origin presente (o navegador é a autoridade).
		mockMvc.perform(get("/developers/origins")
				.header("X-Session-Token", user.token())
				.header("Origin", "https://evil.example")
				.header("X-Client-Origin", APP_ORIGIN))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ORIGIN_NOT_ALLOWED"));

		// Fluxos de infraestrutura/terceiros seguem sem origem.
		mockMvc.perform(get("/version")).andExpect(status().isOk());
		mockMvc.perform(get("/health")).andExpect(status().isOk());
		mockMvc.perform(post("/webhooks/github")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isServiceUnavailable());
		// Token inválido responde 400 pelo próprio controller — o que importa é não ser o 403 do gate.
		mockMvc.perform(get("/blogs/descadastrar").param("token", "invalido"))
			.andExpect(status().isBadRequest());
		mockMvc.perform(get("/media/nao-existe")).andExpect(status().isNotFound());
		mockMvc.perform(get("/pages/nao-existe")).andExpect(status().isNotFound());
	}

	@Test
	void registeredOriginGetsCorsAndInvalidOriginsAreRejected() throws Exception {
		var user = new TestAuthHelper(mockMvc, objectMapper).authenticate("origin_cors_user");
		String session = user.token();

		mockMvc.perform(post("/developers/origins")
				.header("X-Session-Token", session)
				.header("X-Client-Origin", APP_ORIGIN)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"origin\":\"http://app.example\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_ORIGIN"));

		MvcResult created = mockMvc.perform(post("/developers/origins")
				.header("X-Session-Token", session)
				.header("X-Client-Origin", APP_ORIGIN)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"origin\":\"https://app.example/\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.origin").value("https://app.example"))
			.andReturn();
		String id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asString();

		mockMvc.perform(options("/v1")
				.header("Origin", "https://app.example")
				.header("Access-Control-Request-Method", "GET")
				.header("Access-Control-Request-Headers", "X-Session-Token"))
			.andExpect(status().isNoContent())
			.andExpect(header().string("Access-Control-Allow-Origin", "https://app.example"));

		mockMvc.perform(options("/v1")
				.header("Origin", "https://evil.example")
				.header("Access-Control-Request-Method", "GET"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ORIGIN_NOT_ALLOWED"));

		// GET de navegador da origem cadastrada: liberado e com CORS na resposta.
		mockMvc.perform(get("/v1").header("Origin", "https://app.example"))
			.andExpect(status().isOk())
			.andExpect(header().string("Access-Control-Allow-Origin", "https://app.example"));

		// Cliente de servidor da origem cadastrada: liberado sem cabeçalho de CORS.
		mockMvc.perform(get("/v1").header("X-Client-Origin", "https://app.example"))
			.andExpect(status().isOk());

		// Sem nenhuma origem, nem a API pública passa.
		mockMvc.perform(get("/v1"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ORIGIN_REQUIRED"));

		mockMvc.perform(delete("/developers/origins/" + id)
				.header("X-Session-Token", session)
				.header("X-Client-Origin", APP_ORIGIN))
			.andExpect(status().isOk());
	}
}
