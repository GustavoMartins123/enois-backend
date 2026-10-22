package com.socialcoisa.rede.freelances.adapters.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.socialcoisa.rede.shared.web.TestAuthHelper;

import tools.jackson.databind.ObjectMapper;

/** Freela tem tabela própria e exige só faixa de valor, prazo e escopo fechado. */
@SpringBootTest
@AutoConfigureMockMvc
class FreelanceFlowTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void paymentCanBeCashOrDefaultsToFaiscas() throws Exception {
		var owner = new TestAuthHelper(mockMvc, objectMapper).authenticate("free_payment");

		mockMvc.perform(post("/freelances")
				.header("X-Session-Token", owner.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"title", "Site institucional",
					"scope", "Montar o site com cinco páginas e formulário de contato.",
					"budgetMin", 1500,
					"budgetMax", 2500,
					"deadline", LocalDate.now().plusDays(5).toString(),
					"paymentMethod", "CASH"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.paymentMethod").value("CASH"));

		// Sem o campo, o freela continua no circuito da faísca (comportamento antigo).
		mockMvc.perform(post("/freelances")
				.header("X-Session-Token", owner.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"title", "Ajuste de CSS",
					"scope", "Corrigir o layout quebrado na home e no painel administrativo.",
					"budgetMin", 5_000,
					"budgetMax", 8_000,
					"deadline", LocalDate.now().plusDays(5).toString()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.paymentMethod").value("FAISCAS"));

		mockMvc.perform(post("/freelances")
				.header("X-Session-Token", owner.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"title", "Outro trabalho",
					"scope", "Descrição com tamanho suficiente para passar na validação de escopo.",
					"budgetMin", 100,
					"budgetMax", 200,
					"deadline", LocalDate.now().plusDays(5).toString(),
					"paymentMethod", "PIX"))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_PAYMENT_METHOD"));
	}

	@Test
	void createsListsAndClosesAFreelance() throws Exception {
		var owner = new TestAuthHelper(mockMvc, objectMapper).authenticate("free_owner");

		MvcResult created = mockMvc.perform(post("/freelances")
				.header("X-Session-Token", owner.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"title", "Landing page de lançamento",
					"scope", "Entregar uma landing responsiva com formulário de contato e deploy.",
					"budgetMin", 80_000,
					"budgetMax", 150_000,
					"deadline", LocalDate.now().plusDays(10).toString()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("OPEN"))
			.andExpect(jsonPath("$.budgetMin").value(80_000))
			.andReturn();
		String id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asString();

		mockMvc.perform(get("/freelances"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].title").value("Landing page de lançamento"));

		mockMvc.perform(post("/freelances/" + id + "/close").header("X-Session-Token", owner.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CLOSED"));

		mockMvc.perform(get("/freelances"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(0));
	}
}
