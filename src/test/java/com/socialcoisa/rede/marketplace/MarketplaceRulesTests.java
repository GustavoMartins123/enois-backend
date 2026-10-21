package com.socialcoisa.rede.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
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

import com.socialcoisa.rede.coin.application.CoinService;
import com.socialcoisa.rede.marketplace.application.MarketplaceService;
import com.socialcoisa.rede.shared.web.TestAuthHelper;

import tools.jackson.databind.ObjectMapper;

/**
 * Regras do marketplace: preço mínimo de 50 faíscas, nome de quem publicou no catálogo e
 * migração idempotente dos temas da galeria para listagens.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MarketplaceRulesTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private MarketplaceService marketplace;

	private String publishedTheme(TestAuthHelper.AuthSession owner, String name) throws Exception {
		String packageJson = """
				{"palette":{"background":"#ffffff","surface":"#f4f4ff","border":"#dddddd",
				"primary":"#6d28d9","text":{"strong":"#111111","subtle":"#555555"}}}""";

		MvcResult result = mockMvc.perform(post("/temas/galeria")
				.header("X-Session-Token", owner.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", name,
					"description", "Tema de teste",
					"packageJson", packageJson,
					"modeDefault", "light"))))
			.andExpect(status().isOk())
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
	}

	@Test
	void priceFloorAndSellerNameInTheCatalog() throws Exception {
		var auth = new TestAuthHelper(mockMvc, objectMapper);
		var owner = auth.authenticate("mkt_rules_owner");
		String themeId = publishedTheme(owner, "Tema do dono");

		// Abaixo do piso: recusado antes de qualquer listagem.
		mockMvc.perform(post("/marketplace")
				.header("X-Session-Token", owner.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"itemKind", "THEME",
					"itemId", themeId,
					"title", "Tema do dono",
					"price", CoinService.FAIS(49)))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("PRICE_TOO_LOW"));

		// No piso: listado.
		mockMvc.perform(post("/marketplace")
				.header("X-Session-Token", owner.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"itemKind", "THEME",
					"itemId", themeId,
					"title", "Tema do dono",
					"price", CoinService.FAIS(50)))))
			.andExpect(status().isOk());

		// O catálogo mostra quem publicou (nome e @).
		String profile = mockMvc.perform(get("/users/by-id/" + owner.userId()))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		String expectedUsername = objectMapper.readTree(profile).get("username").asText();

		String catalog = mockMvc.perform(get("/marketplace"))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		boolean found = false;
		for (var item : objectMapper.readTree(catalog)) {
			if (themeId.equals(item.get("itemId").asText())) {
				assertThat(item.get("sellerUsername").asText()).isEqualTo(expectedUsername);
				assertThat(item.get("sellerName").asText()).isEqualTo("mkt_rules_owner");
				found = true;
			}
		}
		assertThat(found).isTrue();
	}

	@Test
	void ensureListedIsIdempotentForTheGalleryMigration() throws Exception {
		String itemId = "theme_migracao_teste";

		assertThat(marketplace.ensureListed("user_migracao", "THEME", itemId, "Tema migrado", null,
			MarketplaceService.MIN_PRICE)).isPresent();
		assertThat(marketplace.ensureListed("user_migracao", "THEME", itemId, "Tema migrado", null,
			MarketplaceService.MIN_PRICE)).isEmpty();
	}
}
