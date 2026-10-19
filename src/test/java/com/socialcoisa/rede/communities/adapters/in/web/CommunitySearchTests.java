package com.socialcoisa.rede.communities.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
 * Busca de comunidades por nome ou descricao (GET /communities?q=), sem diferenciar maiusculas.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CommunitySearchTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void buscaPorNomeEDescricaoSemDiferenciarCaixa() throws Exception {
		var dono = new TestAuthHelper(mockMvc, objectMapper).authenticate("comm-search-dono");
		String tag = "zqx" + UUID.randomUUID().toString().substring(0, 6);
		String outroId = criar(dono, "Comunidade Fora do Filtro " + UUID.randomUUID(), "sem a marca", null);

		String porNome = criar(dono, "Comunidade " + tag + " Alpha", "descricao comum", "TECNOLOGIA");
		String porDescricao = criar(dono, "Beta " + UUID.randomUUID(), "fala sobre " + tag, "TECNOLOGIA");

		List<String> encontrados = buscar("q=" + tag);
		assertThat(encontrados).contains(porNome, porDescricao).doesNotContain(outroId);

		// Caixa nao importa para o LIKE.
		assertThat(buscar("q=" + tag.toUpperCase())).contains(porNome, porDescricao);
	}

	private String criar(TestAuthHelper.AuthSession dono, String nome, String descricao, String topic) throws Exception {
		Map<String, Object> body = topic == null
			? Map.of("name", nome, "description", descricao)
			: Map.of("name", nome, "description", descricao, "topic", topic);
		MvcResult criada = mockMvc.perform(post("/communities")
				.header("X-Session-Token", dono.token())
				.header("Idempotency-Key", "comm-search-" + UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(body)))
			.andExpect(status().isCreated())
			.andReturn();
		return objectMapper.readTree(criada.getResponse().getContentAsString()).get("id").asText();
	}

	private List<String> buscar(String query) throws Exception {
		MvcResult resultado = mockMvc.perform(get("/communities?" + query + "&limit=50"))
			.andExpect(status().isOk())
			.andReturn();
		JsonNode items = objectMapper.readTree(resultado.getResponse().getContentAsString()).get("items");
		List<String> ids = new ArrayList<>();
		for (JsonNode item : items) {
			ids.add(item.get("id").asText());
		}
		return ids;
	}
}
