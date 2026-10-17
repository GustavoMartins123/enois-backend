package com.socialcoisa.rede.gifs.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.test.web.servlet.MockMvc;

import com.socialcoisa.rede.gifs.application.GifItem;
import com.socialcoisa.rede.gifs.application.port.out.GifProvider;
import com.socialcoisa.rede.shared.web.ApiException;
import com.socialcoisa.rede.shared.web.TestAuthHelper;

import tools.jackson.databind.ObjectMapper;

/**
 * Busca de GIF do seletor, com um provedor de mentira no lugar do Tenor/GIPHY (nao se testa rede de
 * terceiro). O que importa aqui: sessao obrigatoria, formato da resposta, teto do limite, cache do
 * termo repetido e o erro do provedor virando 502 em vez de derrubar a requisicao.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(GifSearchTests.StubProviderConfig.class)
class GifSearchTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	private TestAuthHelper auth;

	@BeforeEach
	void setUp() {
		auth = new TestAuthHelper(mockMvc, objectMapper);
		StubGifProvider.calls.set(0);
	}

	@Test
	void requiresSession() throws Exception {
		mockMvc.perform(get("/gifs/search").param("q", "gato-sem-sessao"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	@Test
	void returnsItemsFromTheProvider() throws Exception {
		var user = auth.authenticate("user_gif1");

		mockMvc.perform(get("/gifs/search").param("q", "gato").header("X-Session-Token", user.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.available").value(true))
			.andExpect(jsonPath("$.provider").value("Stub"))
			.andExpect(jsonPath("$.items[0].url").value("https://media.tenor.com/abc/gato.gif"))
			.andExpect(jsonPath("$.items[0].previewUrl").value("https://media.tenor.com/abc/gato-tiny.gif"))
			.andExpect(jsonPath("$.items[0].width").value(220));
	}

	@Test
	void cachesTheRepeatedTerm() throws Exception {
		var user = auth.authenticate("user_gif2");

		mockMvc.perform(get("/gifs/search").param("q", "cache-este-termo").header("X-Session-Token", user.token()))
			.andExpect(status().isOk());
		int afterFirst = StubGifProvider.calls.get();

		mockMvc.perform(get("/gifs/search").param("q", "cache-este-termo").header("X-Session-Token", user.token()))
			.andExpect(status().isOk());

		assertThat(afterFirst).isEqualTo(1);
		assertThat(StubGifProvider.calls.get()).isEqualTo(1);
	}

	@Test
	void capsTheLimit() throws Exception {
		var user = auth.authenticate("user_gif3");

		mockMvc.perform(get("/gifs/search").param("q", "limite-alto").param("limit", "500")
				.header("X-Session-Token", user.token()))
			.andExpect(status().isOk());

		assertThat(StubGifProvider.lastLimit.get()).isLessThanOrEqualTo(50);
	}

	@Test
	void turnsProviderFailureInto502() throws Exception {
		var user = auth.authenticate("user_gif4");

		mockMvc.perform(get("/gifs/search").param("q", "falhar").header("X-Session-Token", user.token()))
			.andExpect(status().isBadGateway())
			.andExpect(jsonPath("$.code").value("GIF_PROVIDER_ERROR"));
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class StubProviderConfig {

		@Bean
		@Order(0)
		GifProvider stubGifProvider() {
			return new StubGifProvider();
		}
	}

	static class StubGifProvider implements GifProvider {

		static final AtomicInteger calls = new AtomicInteger();
		static final AtomicInteger lastLimit = new AtomicInteger();

		@Override
		public String name() {
			return "Stub";
		}

		@Override
		public boolean available() {
			return true;
		}

		@Override
		public List<GifItem> search(String query, int limit) {
			calls.incrementAndGet();
			lastLimit.set(limit);
			if ("falhar".equals(query)) {
				throw new ApiException(502, "GIF_PROVIDER_ERROR", "A busca de GIF nao respondeu. Tente de novo.");
			}
			return List.of(new GifItem("g1", "https://media.tenor.com/abc/gato.gif",
				"https://media.tenor.com/abc/gato-tiny.gif", 220, 220, "um gato", "Stub"));
		}
	}
}
