package com.socialcoisa.rede.corporate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

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
 * O fluxo completo que a dona relatou (2026-10-20): preencher o perfil social, criar o perfil
 * profissional, importar TODOS os campos e conferir que ELES FICAM — no GET imediato e depois
 * de reabrir. Se o import persiste mas a tela não recarrega, o bug é do front (o formulário
 * mantém os valores antigos e o próximo "Salvar" apaga a importação).
 */
@SpringBootTest
@AutoConfigureMockMvc
class CorporateImportPersistenceTests {

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
	void importingAllFieldsPersistsAndReadsBack() throws Exception {
		var user = auth.authenticate("import_persist_user");

		// Perfil social preenchido (o que a tela "trazer" oferece vem daqui).
		mockMvc.perform(patch("/me/profile")
				.header("X-Session-Token", user.token())
				.header("Idempotency-Key", "import-persist-profile")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(java.util.Map.of(
					"name", "Maria Social", "headline", "Engenheira de dados",
					"bio", "Bio social completa.", "location", "Recife, PE"))))
			.andExpect(status().isOk());

		mockMvc.perform(post("/me/business-profile")
				.header("X-Session-Token", user.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(java.util.Map.of(
					"username", "import.persist", "name", "Nome Profissional Inicial"))))
			.andExpect(status().isOk());

		// Importa TODOS os campos que o preview oferece com conteúdo.
		var preview = mockMvc.perform(get("/me/business-profile/import-preview")
				.header("X-Session-Token", user.token()))
			.andExpect(status().isOk())
			.andReturn();
		var previewJson = objectMapper.readTree(preview.getResponse().getContentAsString());
		var selected = new java.util.ArrayList<String>();
		previewJson.properties().forEach(entry -> {
			if (entry.getValue().asText().length() > 0) {
				selected.add(entry.getKey());
			}
		});
		mockMvc.perform(post("/me/business-profile/import")
				.header("X-Session-Token", user.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(java.util.Map.of("fields", selected))))
			.andExpect(status().isOk());

		// O GET imediato (e o de uma "reabertura") mostram os dados importados.
		mockMvc.perform(get("/me/business-profile").header("X-Session-Token", user.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Maria Social"))
			.andExpect(jsonPath("$.headline").value("Engenheira de dados"))
			.andExpect(jsonPath("$.bio").value("Bio social completa."))
			.andExpect(jsonPath("$.location").value("Recife, PE"));
	}
}
