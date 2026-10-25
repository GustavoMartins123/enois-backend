package com.socialcoisa.rede.shared.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;

/**
 * Campo de texto que passa do teto da coluna tem de ser recusado com **400 que diz o campo**.
 *
 * Sem a checagem, o INSERT falhava com "Data too long for column" e o tradutor de exceções
 * devolvia **409 CONFLICT** — "Operação conflita com o estado atual dos dados". Para a pessoa que
 * PUBLICA uma empresa ou um evento, essa mensagem não significa nada: que conflito, com qual
 * estado? (No log do servidor o motivo ficava, invisível para ela.)
 *
 * O teto é o mesmo nos três lugares: a coluna do banco, a checagem da API e o contador do front.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TextFieldLimitTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void empresaComNomeLongoRecusaDizendoOCampo() throws Exception {
		var dono = auth("tfl_empresa");
		mockMvc.perform(post("/empresas")
				.header("X-Session-Token", dono.token())
				.header("Idempotency-Key", "tfl-empresa-longa")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "E".repeat(200), "description", "Empresa de teste."))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("COMPANY_FIELD_TOO_LONG"))
			.andExpect(jsonPath("$.message").value(Matchers.containsString("nome")))
			.andExpect(jsonPath("$.message").value(Matchers.containsString("160")));
	}

	@Test
	void eventoComTituloLongoRecusaDizendoOCampo() throws Exception {
		var dono = auth("tfl_evento");
		mockMvc.perform(post("/events")
				.header("X-Session-Token", dono.token())
				.header("Idempotency-Key", "tfl-evento-longo")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"title", "T".repeat(300), "date", "2030-01-02", "time", "19:00"))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("EVENT_FIELD_TOO_LONG"))
			.andExpect(jsonPath("$.message").value(Matchers.containsString("titulo")))
			.andExpect(jsonPath("$.message").value(Matchers.containsString("255")));
	}

	@Test
	void freelaComTituloLongoRecusaDizendoOCampo() throws Exception {
		var dono = auth("tfl_freela");
		mockMvc.perform(post("/freelances")
				.header("X-Session-Token", dono.token())
				.header("Idempotency-Key", "tfl-freela-longo")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"title", "T".repeat(300),
					"scope", "Escopo com pelo menos vinte caracteres para passar da validacao minima.",
					"budgetMin", 10, "budgetMax", 20,
					"deadline", "2030-01-02", "paymentMethod", "FAISCAS"))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("FREELANCE_FIELD_TOO_LONG"))
			.andExpect(jsonPath("$.message").value(Matchers.containsString("titulo")))
			.andExpect(jsonPath("$.message").value(Matchers.containsString("200")));
	}

	private TestAuthHelper.AuthSession auth(String username) throws Exception {
		return new TestAuthHelper(mockMvc, objectMapper).authenticate(username);
	}
}
