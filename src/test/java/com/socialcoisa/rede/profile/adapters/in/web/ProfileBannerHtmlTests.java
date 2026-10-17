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
import com.socialcoisa.rede.shared.web.TestAuthHelper;

/**
 * Capa do perfil em HTML/CSS: alternativa a foto, com o mesmo tratamento da bio (limpeza na
 * gravacao, teto proprio do perfil). A bio tem teste proprio e nao e tocada por isto.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProfileBannerHtmlTests {

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
	void sanitizesBannerHtmlAndKeepsThePhotoToo() throws Exception {
		var user = auth.authenticate("user_banner_html1");
		String html = "<div style=\"background:linear-gradient(90deg,#7c3aed,#ec4899)\">capa</div>"
			+ "<script>alert(1)</script>";

		MvcResult saved = mockMvc.perform(patch("/me/profile")
				.header("X-Session-Token", user.token())
				.header("Idempotency-Key", "banner-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(Map.of("banner", "https://ik.imagekit.io/x/capa.jpg", "bannerHtml", html))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.banner").value("https://ik.imagekit.io/x/capa.jpg"))
			.andReturn();

		JsonNode savedBody = objectMapper.readTree(saved.getResponse().getContentAsString());
		assertThat(savedBody.get("bannerHtml") == null || savedBody.get("bannerHtml").isNull()).isTrue();

		String stored = bannerHtmlOf("/me", user.token());
		assertThat(stored).contains("capa").contains("#7c3aed");
		assertThat(stored).doesNotContain("<script").doesNotContain("alert(");
		assertThat(bannerHtmlOf("/users/" + savedBody.get("username").asText(), user.token())).isEqualTo(stored);
	}

	@Test
	void emptyBannerHtmlClearsItAndKeepsThePhoto() throws Exception {
		var user = auth.authenticate("user_banner_html2");
		save(user.token(), "banner-2a", Map.of("banner", "https://ik.imagekit.io/x/foto.jpg",
			"bannerHtml", "<b>capa html</b>"));
		assertThat(bannerHtmlOf("/me", user.token())).isEqualTo("<b>capa html</b>");

		save(user.token(), "banner-2b", Map.of("bannerHtml", ""));

		MvcResult read = mockMvc.perform(get("/me").header("X-Session-Token", user.token()))
			.andExpect(status().isOk()).andReturn();
		JsonNode node = objectMapper.readTree(read.getResponse().getContentAsString());
		assertThat(node.get("bannerHtml") == null || node.get("bannerHtml").isNull()).isTrue();
		assertThat(node.get("banner").asText()).isEqualTo("https://ik.imagekit.io/x/foto.jpg");
	}

	@Test
	void partialUpdateKeepsTheBannerHtml() throws Exception {
		var user = auth.authenticate("user_banner_html3");
		save(user.token(), "banner-3a", Map.of("bannerHtml", "<i>fica</i>"));
		save(user.token(), "banner-3b", Map.of("name", "Nome Novo"));

		assertThat(bannerHtmlOf("/me", user.token())).isEqualTo("<i>fica</i>");
	}

	private void save(String token, String key, Map<String, Object> fields) throws Exception {
		mockMvc.perform(patch("/me/profile")
				.header("X-Session-Token", token)
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(fields)))
			.andExpect(status().isOk());
	}

	private String bannerHtmlOf(String path, String token) throws Exception {
		MvcResult read = mockMvc.perform(get(path).header("X-Session-Token", token)).andExpect(status().isOk())
			.andReturn();
		JsonNode node = objectMapper.readTree(read.getResponse().getContentAsString()).get("bannerHtml");
		return node == null || node.isNull() ? null : node.asText();
	}

	private String body(Map<String, Object> fields) throws Exception {
		return objectMapper.writeValueAsString(new HashMap<>(fields));
	}
}
