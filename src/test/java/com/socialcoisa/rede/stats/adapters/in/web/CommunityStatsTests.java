package com.socialcoisa.rede.stats.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.socialcoisa.rede.shared.web.TestAuthHelper;

/**
 * Contador de usuarios ativos da landing: numeros publicos, definicao explicita e cache.
 *
 * O cache e o ponto sensivel aqui — sem ele a landing pagaria uma varredura de sessoes, posts e
 * comentarios a cada visita. O teste limpa o cache para poder afirmar o efeito do banco.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CommunityStatsTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private CacheManager cacheManager;

	private TestAuthHelper auth;

	@BeforeEach
	void setUp() {
		auth = new TestAuthHelper(mockMvc, objectMapper);
		clearCache();
	}

	@Test
	void exposesTheActiveWindowAndDoesNotCountAnonymousVisitors() throws Exception {
		// Sem sessao: a landing mostra o contador para quem ainda nao entrou.
		mockMvc.perform(get("/stats/community"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.windowDays").value(30))
			.andExpect(jsonPath("$.computedAt").isNotEmpty())
			.andExpect(jsonPath("$.totalUsers").isNumber())
			.andExpect(jsonPath("$.activeUsers").isNumber());
	}

	@Test
	void cachesTheCountAndPicksUpNewActivityAfterTheCacheExpires() throws Exception {
		long activeBefore = fetch().get("activeUsers").asLong();
		long totalBefore = fetch().get("totalUsers").asLong();

		// Usuario novo entra pela sessao viva (o cadastro/login emite sessao com refresh valido).
		auth.authenticate("user_stats_active");

		// Com o cache quente, o numero NAO muda: e exatamente isso que evita ir ao banco a cada
		// visita a landing. (Se o contador batesse no banco, este assert falharia.)
		assertThat(fetch().get("activeUsers").asLong()).isEqualTo(activeBefore);

		// Cache expirado, o numero reflete o banco de novo.
		clearCache();
		JsonNode refreshed = fetch();
		assertThat(refreshed.get("totalUsers").asLong()).isEqualTo(totalBefore + 1);
		assertThat(refreshed.get("activeUsers").asLong()).isGreaterThanOrEqualTo(activeBefore + 1);
		assertThat(refreshed.get("activeUsers").asLong()).isLessThanOrEqualTo(refreshed.get("totalUsers").asLong());
	}

	private JsonNode fetch() throws Exception {
		MvcResult result = mockMvc.perform(get("/stats/community")).andExpect(status().isOk()).andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}

	private void clearCache() {
		Cache cache = cacheManager.getCache("communityStats");
		assertThat(cache).as("cache do contador declarado em CacheConfig").isNotNull();
		cache.clear();
	}
}
