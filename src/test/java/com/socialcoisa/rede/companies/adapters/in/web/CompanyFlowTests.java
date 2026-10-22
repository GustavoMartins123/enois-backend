package com.socialcoisa.rede.companies.adapters.in.web;

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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.socialcoisa.rede.shared.web.TestAuthHelper;

/**
 * O ciclo inteiro da pagina empresarial com microprojetos: cria pagina, publica microprojeto
 * (entregaveis e criterios de aceite obrigatorios), profissional se candidata, empresa
 * seleciona (IN_PROGRESS) e conclui (COMPLETED). Dono nao se candidata a projeto proprio.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CompanyFlowTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void companyMicroProjectFlowEndToEnd() throws Exception {
		var owner = auth("cflow_owner");
		var pro = auth("cflow_pro");

		MvcResult created = mockMvc.perform(post("/empresas")
				.header("X-Session-Token", owner.token())
				.header("Idempotency-Key", "cflow-create")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "Estudio Game Dev", "description", "Estudio de jogos indie.",
					"sector", "Tecnologia", "size", "1-10", "location", "Remoto"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.slug").value("estudio-game-dev"))
			.andReturn();
		String companyId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

		// Falta entregaveis/criterios: recusa.
		mockMvc.perform(post("/empresas/" + companyId + "/microprojetos")
				.header("X-Session-Token", owner.token())
				.header("Idempotency-Key", "cflow-bad")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"title", "Prototipo de menu", "problem", "problema curto demais"))))
			.andExpect(status().isBadRequest());

		MvcResult project = mockMvc.perform(post("/empresas/" + companyId + "/microprojetos")
				.header("X-Session-Token", owner.token())
				.header("Idempotency-Key", "cflow-project")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"title", "Prototipo de menu principal em Unity",
					"problem", "O menu atual traveta em mobile e precisamos de um prototipo navegavel com foco em gamepad.",
					"scope", "Somente a cena de menu; gameplay fora do escopo.",
					"deliverables", "Cena Unity navegavel\nBuild WebGL",
					"acceptanceCriteria", "Navega com gamepad sem travar; roda em WebGL 60fps",
					"budget", "R$ 2.500", "deadline", "2026-12-31"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("OPEN"))
			.andReturn();
		String projectId = objectMapper.readTree(project.getResponse().getContentAsString()).get("id").asText();

		mockMvc.perform(get("/microprojetos?limit=10"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.id == '" + projectId + "')].status").value("OPEN"));

		MvcResult proposal = mockMvc.perform(post("/microprojetos/" + projectId + "/proposals")
				.header("X-Session-Token", pro.token())
				.header("Idempotency-Key", "cflow-prop-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"message", "Faco isso em uma semana; ja entreguei menus com gamepad em dois jogos."))))
			.andExpect(status().isOk())
			.andReturn();
		String proposalId = objectMapper.readTree(proposal.getResponse().getContentAsString()).get("id").asText();

		// Dono nao se candidata a projeto da propria pagina.
		mockMvc.perform(post("/microprojetos/" + projectId + "/proposals")
				.header("X-Session-Token", owner.token())
				.header("Idempotency-Key", "cflow-prop-2")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("message", "candidatura do proprio dono"))))
			.andExpect(status().isForbidden());

		mockMvc.perform(get("/microprojetos/" + projectId)
				.header("X-Session-Token", owner.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.proposals[0].status").value("SENT"));

		mockMvc.perform(post("/microprojetos/" + projectId + "/proposals/" + proposalId + "/select")
				.header("X-Session-Token", owner.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("IN_PROGRESS"));

		mockMvc.perform(post("/microprojetos/" + projectId + "/complete")
				.header("X-Session-Token", owner.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("COMPLETED"));

		mockMvc.perform(get("/empresa/estudio-game-dev"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Estudio Game Dev"))
			.andExpect(jsonPath("$.projects[0].status").value("COMPLETED"));
	}

	private TestAuthHelper.AuthSession auth(String name) throws Exception {
		return new TestAuthHelper(mockMvc, objectMapper).authenticate(name);
	}
}
