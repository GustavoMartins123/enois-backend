package com.socialcoisa.rede.groups;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.socialcoisa.rede.shared.web.TestAuthHelper;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Identidade visual do grupo pela edição (logo, banner em foto ou HTML).
 *
 * O que este teste tranca:
 * - o dono define avatar e banner e a página passa a devolvê-los;
 * - o banner em HTML é sanitizado (script não é guardado);
 * - mandar o campo em BRANCO remove a imagem (antes o branco virava nulo e a remoção era
 *   impossível pela edição);
 * - campo ausente no PATCH mantém o que estava.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GroupIdentityTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	private TestAuthHelper authHelper() {
		return new TestAuthHelper(mockMvc, objectMapper);
	}

	private String createGroup(TestAuthHelper.AuthSession dono, String name, String key) throws Exception {
		MvcResult result = mockMvc.perform(post("/groups")
				.header("X-Session-Token", dono.token())
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("name", name, "joinMode", "OPEN"))))
			.andExpect(status().isCreated())
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
	}

	private JsonNode group(String slug, TestAuthHelper.AuthSession dono) throws Exception {
		MvcResult result = mockMvc.perform(get("/groups/" + slug).header("X-Session-Token", dono.token()))
			.andExpect(status().isOk())
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}

	private void update(TestAuthHelper.AuthSession dono, String groupId, Map<String, Object> fields, String key)
			throws Exception {
		mockMvc.perform(patch("/groups/" + groupId)
				.header("X-Session-Token", dono.token())
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(fields)))
			.andExpect(status().isOk());
	}

	@Test
	void donoDefineELimpaLogoEBanner() throws Exception {
		var dono = authHelper().authenticate("gid-dono");
		String groupId = createGroup(dono, "Identidade do Grupo", "gid-create");

		Map<String, Object> comImagens = new LinkedHashMap<>();
		comImagens.put("avatar", "https://cdn.enois.ia.br/logo.png");
		comImagens.put("banner", "https://cdn.enois.ia.br/capa.png");
		update(dono, groupId, comImagens, "gid-set");

		JsonNode withImages = group("identidade-do-grupo", dono);
		assertThat(withImages.get("avatar").asText()).isEqualTo("https://cdn.enois.ia.br/logo.png");
		assertThat(withImages.get("banner").asText()).isEqualTo("https://cdn.enois.ia.br/capa.png");

		// Campo ausente mantém o que estava (o PATCH é parcial).
		update(dono, groupId, Map.of("description", "nova descricao"), "gid-keep");
		JsonNode kept = group("identidade-do-grupo", dono);
		assertThat(kept.get("avatar").asText()).isEqualTo("https://cdn.enois.ia.br/logo.png");
		assertThat(kept.get("banner").asText()).isEqualTo("https://cdn.enois.ia.br/capa.png");

		// Em branco remove as duas imagens.
		Map<String, Object> limpar = new LinkedHashMap<>();
		limpar.put("avatar", "");
		limpar.put("banner", "");
		update(dono, groupId, limpar, "gid-clear");

		JsonNode cleared = group("identidade-do-grupo", dono);
		assertThat(cleared.get("avatar").isNull()).isTrue();
		assertThat(cleared.get("banner").isNull()).isTrue();
	}

	@Test
	void bannerHtmlEhSanitizadoEPodeSerLimpo() throws Exception {
		var dono = authHelper().authenticate("gid-html-dono");
		String groupId = createGroup(dono, "Capa HTML do Grupo", "gid-html-create");

		Map<String, Object> comHtml = new LinkedHashMap<>();
		comHtml.put("bannerHtml", "<div style=\"color:red\">capa</div><script>alert(1)</script>");
		update(dono, groupId, comHtml, "gid-html-set");

		JsonNode withHtml = group("capa-html-do-grupo", dono);
		String bannerHtml = withHtml.get("bannerHtml").asText();
		assertThat(bannerHtml).contains("capa").doesNotContain("<script");

		// Em branco limpa o cabeçalho HTML (a foto, se existir, volta a valer).
		update(dono, groupId, Map.of("bannerHtml", ""), "gid-html-clear");
		JsonNode cleared = group("capa-html-do-grupo", dono);
		assertThat(cleared.get("bannerHtml").asText()).isEmpty();
	}
}
