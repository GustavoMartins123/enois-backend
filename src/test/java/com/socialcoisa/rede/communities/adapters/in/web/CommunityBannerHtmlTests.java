package com.socialcoisa.rede.communities.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
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
import com.socialcoisa.rede.shared.web.TestDatabase;

/**
 * Capa de comunidade em HTML/CSS, igual a do perfil: sanitizada no servidor (animação entra,
 * script não), dono edita, campo ausente mantém e vazio limpa.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CommunityBannerHtmlTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	private TestAuthHelper auth;
	private String communityId;

	@BeforeEach
	void setUp() {
		auth = new TestAuthHelper(mockMvc, objectMapper);
	}

	@AfterEach
	void cleanUp() {
		if (communityId != null) {
			TestDatabase.deleteCommunity(communityId);
		}
	}

	private String create(String name, String ownerName) throws Exception {
		MvcResult result = mockMvc.perform(post("/communities")
				.header("X-Session-Token", auth(ownerName).token())
				.header("Idempotency-Key", "banner-comm-" + name)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", name, "description", "d", "topic", "Tecnologia"))))
			.andExpect(status().isCreated()).andReturn();
		communityId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
		return communityId;
	}

	private TestAuthHelper.AuthSession auth(String name) throws Exception {
		return new TestAuthHelper(mockMvc, objectMapper).authenticate(name);
	}

	private String patchBanner(String bannerHtml, TestAuthHelper.AuthSession session) throws Exception {
		MvcResult result = mockMvc.perform(patch("/communities/" + communityId)
				.header("X-Session-Token", session.token())
				.header("Idempotency-Key", "banner-patch-" + System.nanoTime())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "Comunidade Banner", "description", "d", "topic", "Tecnologia",
					"cover", "", "avatar", "", "bannerHtml", bannerHtml))))
			.andExpect(status().isOk()).andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).path("bannerHtml").asString();
	}

	@Test
	void bannerHtmlKeepsAnimationAndClears() throws Exception {
		var owner = auth("banner_owner");
		communityId = create("Comunidade Banner", "banner_owner");

		String saved = patchBanner("""
			<style>
			  @keyframes brilho { 0% { opacity: .6; } 100% { opacity: 1; } }
			  .capa { animation: brilho 2s infinite alternate; }
			</style>
			<div class="capa">capa da comunidade</div>
			""", owner);
		assertThat(saved).contains("@keyframes brilho").contains("animation: brilho 2s infinite");

		// Vazio limpa: a capa volta para a foto (null). NullNode.asString() devolve "", entao a
		// leitura do JSON precisa tratar nulo de verdade.
		MvcResult cleared = mockMvc.perform(patch("/communities/" + communityId)
				.header("X-Session-Token", owner.token())
				.header("Idempotency-Key", "banner-clear-" + System.nanoTime())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "Comunidade Banner", "description", "d", "topic", "Tecnologia",
					"cover", "", "avatar", "", "bannerHtml", ""))))
			.andExpect(status().isOk()).andReturn();
		assertThat(objectMapper.readTree(cleared.getResponse().getContentAsString())
			.path("bannerHtml").isNull()).isTrue();

		// Script sai, o resto fica.
		String safe = patchBanner("<style>.x{}</style><div onclick=\"alert(1)\"><script>alert(2)</script>x</div>",
			owner);
		assertThat(safe).doesNotContain("script").doesNotContain("onclick").contains("<div>x</div>");
	}

	@Test
	void nonOwnerCannotSetBannerHtml() throws Exception {
		var owner = auth("banner_owner2");
		var intruder = auth("banner_intruder2");
		communityId = create("Comunidade Banner 2", "banner_owner2");

		mockMvc.perform(patch("/communities/" + communityId)
				.header("X-Session-Token", intruder.token())
				.header("Idempotency-Key", "banner-patch-intruder")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "Comunidade Banner 2", "description", "d", "topic", "Tecnologia",
					"bannerHtml", "<style>.x{}</style>"))))
			.andExpect(status().isForbidden());
	}
}
