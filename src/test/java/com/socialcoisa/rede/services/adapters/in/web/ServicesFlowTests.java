package com.socialcoisa.rede.services.adapters.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.ObjectMapper;

import com.socialcoisa.rede.shared.web.TestAuthHelper;

/**
 * Os serviços freelance (Fase 5): o pedido exige entregáveis; a proposta precisa estar
 * na faixa; escolher RETÉM as faíscas (escrow); entregar → aceitar libera 90% para o
 * profissional; reembolso devolve 100%. Teto de 5 ofertas abertas por conta.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ServicesFlowTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void listingContractDeliverAndAccept() throws Exception {
		var seller = auth("svc_seller");
		var buyer = auth("svc_buyer");
		wallet(seller.token());
		wallet(buyer.token());

		// Publica a oferta por 30 faíscas.
		MvcResult created = mockMvc.perform(post("/servicos/ofertas")
				.header("X-Session-Token", seller.token())
				.header("Idempotency-Key", "svc-listing-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"category", "design", "title", "Logo simples para o seu time",
					"description", "Faço um logo limpo com duas rodadas de ajuste, formato pronto para uso.",
					"deliverables", "SVG + PNG em três tamanhos", "price", 30, "deliveryDays", 5))))
			.andExpect(status().isOk())
			.andReturn();
		String listingId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

		// O vendedor não contrata o próprio serviço.
		mockMvc.perform(post("/servicos/ofertas/" + listingId + "/contratar")
				.header("X-Session-Token", seller.token()))
			.andExpect(status().isBadRequest());

		// O comprador contrata: escrow retém (100 -> 70).
		MvcResult contract = mockMvc.perform(post("/servicos/ofertas/" + listingId + "/contratar")
				.header("X-Session-Token", buyer.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.state").value("HELD"))
			.andReturn();
		String contractId = objectMapper.readTree(contract.getResponse().getContentAsString()).get("contractId").asText();
		balance(buyer.token(), 70);

		// O vendedor entrega e o comprador aceita: 90% (27) para o vendedor.
		mockMvc.perform(post("/servicos/contratos/" + contractId + "/entregar")
				.header("X-Session-Token", seller.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("note", "SVG e PNG entregues no link X"))))
			.andExpect(status().isOk());
		mockMvc.perform(post("/servicos/contratos/" + contractId + "/aceitar")
				.header("X-Session-Token", buyer.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.state").value("RELEASED"));
		balance(seller.token(), 127);
	}

	@Test
	void requestProposalsChooseRefundAndDeliverablesRequired() throws Exception {
		var requester = auth("svc_requester");
		var pro = auth("svc_pro");
		wallet(requester.token());
		wallet(pro.token());

		// Sem entregáveis o pedido nem abre (a lição dos microprojetos).
		mockMvc.perform(post("/servicos/pedidos")
				.header("X-Session-Token", requester.token())
				.header("Idempotency-Key", "svc-req-bad")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"category", "código", "title", "Página para a minha comunidade",
					"description", "Uma página institucional simples com formulário de contato funcional.",
					"budgetMin", 40, "budgetMax", 80))))
			.andExpect(status().isBadRequest());

		// Pedido correto, na faixa 40–80.
		MvcResult created = mockMvc.perform(post("/servicos/pedidos")
				.header("X-Session-Token", requester.token())
				.header("Idempotency-Key", "svc-req-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"category", "código", "title", "Página para a minha comunidade",
					"description", "Uma página institucional simples com formulário de contato funcional.",
					"deliverables", "HTML + CSS publicados", "budgetMin", 40, "budgetMax", 80))))
			.andExpect(status().isOk())
			.andReturn();
		String requestId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

		// Proposta fora da faixa é recusada; dentro, aceita.
		mockMvc.perform(post("/servicos/pedidos/" + requestId + "/propostas")
				.header("X-Session-Token", pro.token())
				.header("Idempotency-Key", "svc-prop-bad")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("message", "Faço isso rapidinho para você.", "price", 200))))
			.andExpect(status().isBadRequest());
		MvcResult proposal = mockMvc.perform(post("/servicos/pedidos/" + requestId + "/propostas")
				.header("X-Session-Token", pro.token())
				.header("Idempotency-Key", "svc-prop-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("message", "Entrego em uma semana, com revisão.", "price", 50))))
			.andExpect(status().isOk())
			.andReturn();
		String proposalId = objectMapper.readTree(proposal.getResponse().getContentAsString()).get("id").asText();

		// Escolher a proposta: escrow retém (100 -> 50) e o pedido fecha.
		mockMvc.perform(post("/servicos/pedidos/" + requestId + "/escolher")
				.header("X-Session-Token", requester.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("proposalId", proposalId))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.state").value("HELD"));
		balance(requester.token(), 50);

		// Reembolso: devolve 100%.
		MvcResult mine = mockMvc.perform(get("/servicos/minhas").header("X-Session-Token", requester.token()))
			.andExpect(status().isOk())
			.andReturn();
		String contractId = objectMapper
			.readTree(mine.getResponse().getContentAsString())
			.at("/contratosComoComprador/0/id").asText();
		mockMvc.perform(post("/servicos/contratos/" + contractId + "/reembolso")
				.header("X-Session-Token", requester.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.state").value("REFUNDED"));
		balance(requester.token(), 100);
	}

	private void wallet(String token) throws Exception {
		mockMvc.perform(get("/carteira").header("X-Session-Token", token)).andExpect(status().isOk());
	}

	private void balance(String token, long expected) throws Exception {
		MvcResult wallet = mockMvc.perform(get("/carteira").header("X-Session-Token", token)).andExpect(status().isOk())
			.andReturn();
		long balance = objectMapper.readTree(wallet.getResponse().getContentAsString()).get("balance").asLong();
		org.assertj.core.api.Assertions.assertThat(balance)
			.as("saldo: " + wallet.getResponse().getContentAsString()).isEqualTo(expected);
	}

	private TestAuthHelper.AuthSession auth(String name) throws Exception {
		return new TestAuthHelper(mockMvc, objectMapper).authenticate(name);
	}
}
