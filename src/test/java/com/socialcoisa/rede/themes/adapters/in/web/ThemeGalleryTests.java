package com.socialcoisa.rede.themes.adapters.in.web;

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
 * Galeria de temas da comunidade: publicar (validacao de schema E de contraste >= 4.5:1),
 * listagem publica, instalar conta visita, denunciar. Tema ilegivel NAO publica.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ThemeGalleryTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	private TestAuthHelper.AuthSession auth(String name) throws Exception {
		return new TestAuthHelper(mockMvc, objectMapper).authenticate(name);
	}

	private static String pkg(String bg, String strong) {
		return "{\"palette\":{\"background\":\"" + bg + "\",\"surface\":\"" + bg
			+ "\",\"muted\":\"" + bg + "\",\"border\":\"" + bg
			+ "\",\"primary\":\"#7c3aed\",\"onPrimary\":\"#ffffff\",\"soft\":\"#ede8f5\",\"text\":{\"strong\":\""
			+ strong + "\",\"subtle\":\"#555555\",\"date\":\"#777777\"}},\"radius\":\"8px\"}";
	}

	@Test
	void publishListInstallAndReport() throws Exception {
		var author = auth("tgal_author");

		MvcResult created = mockMvc.perform(post("/temas/galeria")
				.header("X-Session-Token", author.token())
				.header("Idempotency-Key", "tgal-pub-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "Tema Bonito", "description", "tema de teste", "modeDefault", "light",
					"packageJson", pkg("#f5e6c8", "#3a2a10")))))
			.andExpect(status().isOk())
			.andReturn();
		String themeId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

		mockMvc.perform(get("/temas/galeria?limit=30"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.id == '" + themeId + "')].name").value("Tema Bonito"));

		mockMvc.perform(post("/temas/galeria/" + themeId + "/install"))
			.andExpect(status().isOk());

		mockMvc.perform(post("/temas/galeria/" + themeId + "/report"))
			.andExpect(status().isOk());

		// Pacote invalido (url no lugar de cor) e rejeitado na porta.
		mockMvc.perform(post("/temas/galeria")
				.header("X-Session-Token", author.token())
				.header("Idempotency-Key", "tgal-invalid")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "Tema Ruim", "modeDefault", "light",
					"packageJson", "{\"palette\":{\"background\":\"url(evil)\",\"text\":{\"strong\":\"#000\"}}}"))))
			.andExpect(status().isBadRequest());
	}

	@Test
	void lowContrastThemeIsRejected() throws Exception {
		var author = auth("tgal_author2");

		// Texto quase-branco sobre fundo quase-branco: o gate de contraste recusa.
		mockMvc.perform(post("/temas/galeria")
				.header("X-Session-Token", author.token())
				.header("Idempotency-Key", "tgal-lowc")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "Tema Invisivel", "modeDefault", "light",
					"packageJson", pkg("#f8f8f8", "#f0f0f0")))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("THEME_CONTRAST_LOW"));
	}

	@Test
	void anonymousCanListButNotPublish() throws Exception {
		mockMvc.perform(get("/temas/galeria?limit=5"))
			.andExpect(status().isOk());

		mockMvc.perform(post("/temas/galeria")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("name", "Anon", "modeDefault", "light",
					"packageJson", pkg("#f5e6c8", "#3a2a10")))))
			.andExpect(status().isUnauthorized());
	}
}
