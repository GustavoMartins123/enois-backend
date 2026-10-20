package com.socialcoisa.rede.jobs.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.socialcoisa.rede.profile.application.ReputationService;
import com.socialcoisa.rede.shared.web.TestAuthHelper;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Cadastro de vaga EXTERNA por uma pessoa da comunidade, de ponta a ponta: a vaga entra na aba
 * "Externas" com o selo "Comunidade", o detalhe aponta para o link de origem (e nao para um
 * formulario do enois), o autor ve e exclui a propria, e ninguem exclui a dos outros.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
	"rede.jobs.import.external-listing-enabled=true",
	"rede.jobs.import.jobradar.enabled=false",
	"rede.jobs.import.external-refresh-enabled=false"
})
class ExternalJobSubmissionWebTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	/** O gate de reputacao e testado em unidade; aqui basta um valor alto e estavel. */
	@MockitoBean
	private ReputationService reputationService;

	private TestAuthHelper auth;

	@BeforeEach
	void setUp() {
		when(reputationService.reputationOf(anyString())).thenReturn(300);
		auth = new TestAuthHelper(mockMvc, objectMapper);
	}

	@Test
	void submittedExternalJobIsListedMarkedAsCommunityAndLinksToTheOrigin() throws Exception {
		var author = auth.authenticate("ext_job_author");
		String title = "Engenheira de Confiabilidade " + System.nanoTime();
		String url = "https://vagas.exemplo.test/oportunidade/" + System.nanoTime();

		MvcResult created = submit(author, title, "Empresa Exemplo", "Remoto", url, "ext-create-1")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.sourceType").value("EXTERNAL"))
			.andExpect(jsonPath("$.sourcePlatform").value("Comunidade"))
			.andExpect(jsonPath("$.sourceUrl").value(url))
			.andExpect(jsonPath("$.owner").value(true))
			.andReturn();
		String id = objectMapper.readTree(created.getResponse().getContentAsString()).path("id").asString();
		assertThat(id).startsWith("ext_com_");

		// Aba "Externas" (com o filtro por plataforma): a vaga da comunidade esta la.
		MvcResult listed = mockMvc.perform(get("/jobs")
				.param("source", "EXTERNAL").param("platform", "Comunidade").param("query", title))
			.andExpect(status().isOk())
			.andReturn();
		JsonNode items = objectMapper.readTree(listed.getResponse().getContentAsString()).get("items");
		assertThat(items.size()).isEqualTo(1);
		assertThat(items.get(0).path("id").asString()).isEqualTo(id);
		assertThat(items.get(0).path("sourceUrl").asString()).isEqualTo(url);

		// O detalhe (compartilhavel) tambem e externo: nada de formulario de candidatura do enois.
		mockMvc.perform(get("/jobs/{id}", id))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.sourceType").value("EXTERNAL"))
			.andExpect(jsonPath("$.sourceUrl").value(url));

		mockMvc.perform(get("/jobs/external/mine").header("X-Session-Token", author.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value(id));

		// Excluir: some da lista publica e da minha.
		mockMvc.perform(delete("/jobs/external/{id}", id)
				.header("X-Session-Token", author.token())
				.header("Idempotency-Key", "ext-delete-1"))
			.andExpect(status().isNoContent());
		mockMvc.perform(get("/jobs/external/mine").header("X-Session-Token", author.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(0));
		mockMvc.perform(get("/jobs/{id}", id)).andExpect(status().isNotFound());
	}

	@Test
	void theSameLinkCannotBeListedTwice() throws Exception {
		var author = auth.authenticate("ext_job_dup");
		String url = "https://vagas.exemplo.test/duplicada/" + System.nanoTime();

		submit(author, "Vaga duplicada", "Empresa", "Remoto", url, "ext-dup-1").andExpect(status().isCreated());
		// Parametro de rastreio nao engana a dedupe: a URL canonica e a mesma.
		submit(author, "Vaga duplicada", "Empresa", "Remoto", url + "?utm_source=telegram", "ext-dup-2")
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("JOB_ALREADY_LISTED"));
	}

	@Test
	void refusesALinkThatIsNotHttps() throws Exception {
		var author = auth.authenticate("ext_job_http");

		submit(author, "Vaga insegura", "Empresa", "Remoto", "http://vagas.exemplo.test/x", "ext-http-1")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_EXTERNAL_JOB"));
	}

	@Test
	void anotherUserCannotDeleteMySubmission() throws Exception {
		var owner = auth.authenticate("ext_job_owner2");
		var intruder = auth.authenticate("ext_job_intruder");
		MvcResult created = submit(owner, "Vaga da dona", "Empresa", "Remoto",
			"https://vagas.exemplo.test/dona/" + System.nanoTime(), "ext-owner-1").andReturn();
		String id = objectMapper.readTree(created.getResponse().getContentAsString()).path("id").asString();

		mockMvc.perform(delete("/jobs/external/{id}", id)
				.header("X-Session-Token", intruder.token())
				.header("Idempotency-Key", "ext-owner-delete-intruder"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("NOT_OWNER"));
	}

	@Test
	void submittedJobCarriesActivitiesSalaryAndApplyEmailAndStaysOutOfAggregators() throws Exception {
		var author = auth.authenticate("ext_job_details");
		String title = "Vaga com detalhes " + System.nanoTime();
		String url = "https://vagas.exemplo.test/detalhes/" + System.nanoTime();
		String payload = objectMapper.writeValueAsString(Map.of(
			"title", title,
			"company", "Empresa Detalhes",
			"location", "Remoto",
			"url", url,
			"description", "Atuar com Java e Spring no time de plataforma.",
			"salaryMin", "8000",
			"salaryMax", "12000",
			"salaryPeriod", "MONTH",
			"applyEmail", "vagas@empresa.test",
			"communityAgreement", true));

		MvcResult created = mockMvc.perform(post("/jobs/external")
				.header("X-Session-Token", author.token())
				.header("Idempotency-Key", "ext-details-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(payload))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.description").value("Atuar com Java e Spring no time de plataforma."))
			.andExpect(jsonPath("$.salaryMin").value("8000"))
			.andExpect(jsonPath("$.salaryMax").value("12000"))
			.andExpect(jsonPath("$.salaryPeriod").value("MONTH"))
			.andExpect(jsonPath("$.salaryLabel").value("de R$ 8.000 até R$ 12.000 / mês"))
			.andExpect(jsonPath("$.applyEmail").value("vagas@empresa.test"))
			.andReturn();
		String id = objectMapper.readTree(created.getResponse().getContentAsString()).path("id").asString();

		// A listagem geral de agregadores NAO devolve a vaga da comunidade.
		MvcResult aggregators = mockMvc.perform(get("/jobs")
				.param("source", "EXTERNAL").param("query", title))
			.andExpect(status().isOk())
			.andReturn();
		JsonNode aggregatorItems = objectMapper.readTree(aggregators.getResponse().getContentAsString())
			.get("items");
		// Jackson 3 (tools.jackson) não tem findValuesAsText: coleta explícita, como no resto do arquivo.
		java.util.List<String> aggregatorIds = new java.util.ArrayList<>();
		for (JsonNode item : aggregatorItems) {
			aggregatorIds.add(item.path("id").asText());
		}
		assertThat(aggregatorIds).doesNotContain(id);

		// O filtro próprio ("Criadas no Énóis" = plataforma Comunidade) devolve.
		mockMvc.perform(get("/jobs")
				.param("source", "EXTERNAL").param("platform", "Comunidade").param("query", title))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].id").value(id));
	}

	private org.springframework.test.web.servlet.ResultActions submit(
			TestAuthHelper.AuthSession session, String title, String company, String location, String url,
			String idempotencyKey) throws Exception {
		String payload = objectMapper.writeValueAsString(Map.of(
			"title", title,
			"company", company,
			"location", location,
			"url", url,
			"communityAgreement", true));
		return mockMvc.perform(post("/jobs/external")
			.header("X-Session-Token", session.token())
			.header("Idempotency-Key", idempotencyKey)
			.contentType(MediaType.APPLICATION_JSON)
			.content(payload));
	}
}
