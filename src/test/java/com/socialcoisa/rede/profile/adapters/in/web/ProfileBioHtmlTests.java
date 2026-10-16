package com.socialcoisa.rede.profile.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.socialcoisa.rede.shared.html.SafeHtml;
import com.socialcoisa.rede.shared.web.TestAuthHelper;

/**
 * Bio do perfil em HTML/CSS: mesma limpeza e mesmo teto do post de pagina, aplicados na gravacao.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProfileBioHtmlTests {

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
	void sanitizesBioHtmlOnSaveAndKeepsItOutOfTheWriteResponse() throws Exception {
		var user = auth.authenticate("user_bio_html1");
		String html = """
			<style>.capa{color:#c00}</style><div class="capa" style="font-weight:600">Ola</div>
			<script>fetch('https://malicioso.test')</script><p onclick="alert(1)">corpo</p>
			""";

		MvcResult saved = mockMvc.perform(patch("/me/profile")
				.header("X-Session-Token", user.token())
				.header("Idempotency-Key", "bio-html-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(Map.of("bio", "Bio de texto", "bioHtml", html))))
			.andExpect(status().isOk())
			.andReturn();

		// A resposta da escrita nao leva o HTML: essa resposta passa pela idempotencia, que guarda o
		// corpo numa coluna de texto (o mesmo estouro que quebrou a publicacao de pagina).
		JsonNode savedBody = objectMapper.readTree(saved.getResponse().getContentAsString());
		assertThat(savedBody.get("bioHtml") == null || savedBody.get("bioHtml").isNull()).isTrue();

		String stored = bioHtmlOf("/me", user.token());
		assertThat(stored).contains("Ola").contains("corpo").contains("#c00");
		assertThat(stored).doesNotContain("<script").doesNotContain("onclick").doesNotContain("malicioso.test");

		// O perfil publico mostra a mesma bio (e a mesma versao limpa, nao existe crua guardada).
		String publicStored = bioHtmlOf("/users/" + savedBody.get("username").asText(), user.token());
		assertThat(publicStored).isEqualTo(stored);
	}

	@Test
	void emptyBioHtmlClearsThePageAndKeepsThePlainBio() throws Exception {
		var user = auth.authenticate("user_bio_html2");
		save(user.token(), "bio-html-clear-a", Map.of("bio", "Texto do perfil", "bioHtml", "<b>negrito</b>"));

		assertThat(bioHtmlOf("/me", user.token())).isEqualTo("<b>negrito</b>");

		// Vazio limpa: e assim que o usuario volta para a bio de texto (o HTML nao e obrigatorio).
		save(user.token(), "bio-html-clear-b", Map.of("bioHtml", ""));

		MvcResult read = mockMvc.perform(get("/me").header("X-Session-Token", user.token()))
			.andExpect(status().isOk())
			.andReturn();
		JsonNode node = objectMapper.readTree(read.getResponse().getContentAsString());
		assertThat(node.get("bioHtml") == null || node.get("bioHtml").isNull()).isTrue();
		assertThat(node.get("bio").asText()).isEqualTo("Texto do perfil");
	}

	@Test
	void partialUpdateWithoutBioHtmlKeepsTheExistingOne() throws Exception {
		var user = auth.authenticate("user_bio_html3");
		save(user.token(), "bio-html-keep-a", Map.of("bioHtml", "<b>fica</b>"));

		// Editar so o nome (como o dialogo de perfil faz) nao pode apagar a bio em HTML.
		save(user.token(), "bio-html-keep-b", Map.of("name", "Nome Novo"));

		assertThat(bioHtmlOf("/me", user.token())).isEqualTo("<b>fica</b>");
	}

	@Test
	void bioHtmlWithoutUsableContentIsRejected() throws Exception {
		var user = auth.authenticate("user_bio_html4");

		mockMvc.perform(patch("/me/profile")
				.header("X-Session-Token", user.token())
				.header("Idempotency-Key", "bio-html-bad")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(Map.of("bioHtml", "<script>alert(1)</script>"))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_PROFILE_HTML"));
	}

	@Test
	void oversizedBioHtmlIsRejected() throws Exception {
		var user = auth.authenticate("user_bio_html5");

		mockMvc.perform(patch("/me/profile")
				.header("X-Session-Token", user.token())
				.header("Idempotency-Key", "bio-html-big")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(Map.of("bioHtml", "<p>" + "a".repeat(SafeHtml.MAX_PROFILE_HTML) + "</p>"))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("PROFILE_HTML_TOO_LONG"));
	}

	private void save(String token, String key, Map<String, Object> fields) throws Exception {
		mockMvc.perform(patch("/me/profile")
				.header("X-Session-Token", token)
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(fields)))
			.andExpect(status().isOk());
	}

	private String bioHtmlOf(String path, String token) throws Exception {
		MvcResult read = mockMvc.perform(get(path).header("X-Session-Token", token)).andExpect(status().isOk())
			.andReturn();
		JsonNode node = objectMapper.readTree(read.getResponse().getContentAsString()).get("bioHtml");
		return node == null || node.isNull() ? null : node.asText();
	}

	/** O corpo do PATCH so manda o que o teste quer mudar; o resto fica como esta (keep). */
	private String body(Map<String, Object> fields) throws Exception {
		return objectMapper.writeValueAsString(new HashMap<>(fields));
	}
}