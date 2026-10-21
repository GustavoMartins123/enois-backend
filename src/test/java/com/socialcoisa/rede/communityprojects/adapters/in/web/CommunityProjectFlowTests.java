package com.socialcoisa.rede.communityprojects.adapters.in.web;

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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.socialcoisa.rede.shared.web.TestAuthHelper;

import tools.jackson.databind.ObjectMapper;

/** Projeto tem tabela, ciclo e página próprios: enviar -> revisar -> publicar -> suspender. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "rede.admin.emails=proj_admin@test.com")
class CommunityProjectFlowTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void submitReviewPublishAndSuspendAProject() throws Exception {
		var creator = new TestAuthHelper(mockMvc, objectMapper).authenticate("proj_creator");
		var admin = new TestAuthHelper(mockMvc, objectMapper).authenticate("proj_admin");

		MvcResult created = mockMvc.perform(post("/community-projects")
				.header("X-Session-Token", creator.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"title", "Painel de acessibilidade",
					"description", "Um painel open source para auditar contraste e foco.",
					"shortDescription", "Auditoria de acessibilidade",
					"url", "https://exemplo.test/projeto",
					"repoUrl", "https://github.com/exemplo/projeto",
					"tags", "acessibilidade,react",
					"category", "WEB"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SUBMITTED"))
			.andReturn();
		String id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asString();

		// Não publicado: catálogo e página pública não mostram.
		mockMvc.perform(get("/community-projects"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(0));
		mockMvc.perform(get("/community-projects/proj-creator/painel-de-acessibilidade"))
			.andExpect(status().isNotFound());

		// A fila da moderação existe e é separada da de jogos.
		mockMvc.perform(get("/moderation/community-projects").header("X-Session-Token", admin.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value(id));

		mockMvc.perform(post("/moderation/community-projects/" + id + "/approve")
				.header("X-Session-Token", admin.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"note\":\"ok\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PUBLISHED"));

		mockMvc.perform(get("/community-projects"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].title").value("Painel de acessibilidade"));

		mockMvc.perform(get("/community-projects/proj-creator/painel-de-acessibilidade"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.repoUrl").value("https://github.com/exemplo/projeto"));

		mockMvc.perform(post("/admin/community-projects/" + id + "/suspend")
				.header("X-Session-Token", admin.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"note\":\"revisar\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SUSPENDED"));

		mockMvc.perform(get("/community-projects/proj-creator/painel-de-acessibilidade"))
			.andExpect(status().isNotFound());
	}
}
