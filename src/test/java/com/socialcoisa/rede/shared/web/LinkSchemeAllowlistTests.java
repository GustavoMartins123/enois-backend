package com.socialcoisa.rede.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.ObjectMapper;

/** Links rendered as {@code href} only carry http, https or mailto, however the scheme is disguised. */
@SpringBootTest
@AutoConfigureMockMvc
class LinkSchemeAllowlistTests {

	private static final List<String> DISGUISED_SCRIPT_URLS = List.of(
		"javascript:alert(1)",
		"  JaVaScRiPt:alert(1)",
		"java\tscript:alert(1)",
		"java\nscript:alert(1)",
		"\u0001javascript:alert(1)",
		"java​script:alert(1)",
		"java\\script:alert(1)",
		"vbscript:msgbox(1)",
		"data:text/html,<script>alert(1)</script>");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void linkTargetAllowsOnlyHttpHttpsMailtoOrNoScheme() {
		for (String url : DISGUISED_SCRIPT_URLS) {
			assertThat(SafeUrl.isSafeLinkTarget(url)).as(url).isFalse();
		}
		for (String url : List.of("https://enois.ia.br", "http://x.test/a?b=c", "mailto:a@b.test", "/perfil/ana",
				"#topo", "")) {
			assertThat(SafeUrl.isSafeLinkTarget(url)).as(url).isTrue();
		}
	}

	@Test
	void companyWebsiteRejectsScriptSchemes() throws Exception {
		var owner = new TestAuthHelper(mockMvc, objectMapper).authenticate("linkscheme_owner");
		MvcResult created = mockMvc.perform(post("/empresas")
				.header("X-Session-Token", owner.token())
				.header("Idempotency-Key", "linkscheme-company")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("name", "Link Scheme Ltda",
					"description", "Empresa de teste.", "sector", "Tecnologia", "size", "1-10",
					"location", "Remoto"))))
			.andExpect(status().isOk())
			.andReturn();
		var company = objectMapper.readTree(created.getResponse().getContentAsString());
		String companyId = company.get("id").asString();

		int attempt = 0;
		for (String website : DISGUISED_SCRIPT_URLS) {
			mockMvc.perform(patch("/empresas/" + companyId)
					.header("X-Session-Token", owner.token())
					.header("Idempotency-Key", "linkscheme-patch-" + attempt++)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(Map.of("name", "Link Scheme Ltda", "website", website))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_URL"));
		}
		mockMvc.perform(patch("/empresas/" + companyId)
				.header("X-Session-Token", owner.token())
				.header("Idempotency-Key", "linkscheme-patch-ok")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("name", "Link Scheme Ltda",
					"website", "https://linkscheme.test"))))
			.andExpect(status().isOk());

		mockMvc.perform(get("/empresa/" + company.get("slug").asString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.website").value("https://linkscheme.test"));
	}

	@Test
	void markdownLinksInAnyBodyFieldRejectScriptSchemes() throws Exception {
		var author = new TestAuthHelper(mockMvc, objectMapper).authenticate("linkscheme_author");
		int attempt = 0;
		for (String url : DISGUISED_SCRIPT_URLS) {
			if (url.contains("\n")) {
				continue; // the front's parser never spans lines
			}
			String target = url.replaceAll("\\(.*", ""); // the destination ends at the first `)`
			mockMvc.perform(post("/feed/posts")
					.header("X-Session-Token", author.token())
					.header("Idempotency-Key", "linkscheme-post-" + attempt++)
					.contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(Map.of("type", "INSIGHT",
						"content", "veja [aqui](" + target + ") e ![img](" + target + ")", "tags", List.of(),
						"mediaIds", List.of()))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("UNSAFE_LINK"));
		}

		// A JSON escape cannot hide the scheme: the check runs on decoded strings.
		mockMvc.perform(post("/feed/posts")
				.header("X-Session-Token", author.token())
				.header("Idempotency-Key", "linkscheme-post-escaped")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"type\":\"INSIGHT\",\"content\":\"[x](\\u006aavascript:alert(1))\",\"tags\":[],"
					+ "\"mediaIds\":[]}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("UNSAFE_LINK"));

		mockMvc.perform(post("/feed/posts")
				.header("X-Session-Token", author.token())
				.header("Idempotency-Key", "linkscheme-post-ok")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("type", "INSIGHT",
					"content", "Data: amanha. Leia [o guia](https://enois.ia.br/guia) ou [escreva](mailto:a@b.test)",
					"tags", List.of(), "mediaIds", List.of()))))
			.andExpect(status().isCreated());
	}
}
