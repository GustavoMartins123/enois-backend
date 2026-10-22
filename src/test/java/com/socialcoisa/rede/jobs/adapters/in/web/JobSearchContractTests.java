package com.socialcoisa.rede.jobs.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.socialcoisa.rede.shared.web.TestDatabase;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Pesquisa booleana nas vagas e combinacao de filtros.
 *
 * O acervo e montado direto no banco (o POST /jobs exige reputacao > 150, o que nao e o alvo aqui) e
 * cada vaga tem um prefixo unico no titulo para o assert nao depender de dados de outros testes.
 */
@SpringBootTest
@AutoConfigureMockMvc
class JobSearchContractTests {

	private static final String PREFIX = "zq";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	private Instant base;

	@BeforeEach
	void seedJobs() {
		// Acervo do teste com data no futuro: outros testes criam vagas "agora" no mesmo banco, e assim
		// a primeira pagina (limit 2) e sempre deste acervo — a paginacao fica determinista.
		base = Instant.now().plusSeconds(3600);
		// Cada teste roda uma vez; as vagas sao re-inseridas com ids estaveis.
		job("java-pleno", "Pessoa Desenvolvedora Java", "Acme Tech", "São Paulo", "CLT", "PLENO",
			List.of("Java", "Spring"), base);
		job("java-senior-remoto", "Pessoa Desenvolvedora Java Senior", "Globex", "Remoto", "PJ", "SENIOR",
			List.of("Java", "Kotlin"), base.minusSeconds(60));
		job("kotlin-junior", "Pessoa Desenvolvedora Kotlin", "Initech", "Recife", "CLT", "JUNIOR",
			List.of("Kotlin"), base.minusSeconds(120));
		job("dados-senior", "Engenheira de Dados", "Acme Tech", "Remoto", "CLT", "SENIOR",
			List.of("Python", "Spark"), base.minusSeconds(180));
		job("estagio-front", "Estágio em Front-end", "Umbrella", "Curitiba", "ESTAGIO", "JUNIOR",
			List.of("React"), base.minusSeconds(240));
		job("porcento", "Analista 100% remoto", "Wayne", "Remoto", "CLT", "PLENO", List.of("SQL"),
			base.minusSeconds(300));
	}

	@Test
	void consecutiveTermsRequireAllOfThem() throws Exception {
		assertThat(ids("java senior")).containsExactlyInAnyOrder("zq-java-senior-remoto");
	}

	@Test
	void orBringsBothSides() throws Exception {
		assertThat(ids("kotlin OR dados")).containsExactlyInAnyOrder("zq-kotlin-junior", "zq-java-senior-remoto",
			"zq-dados-senior");
	}

	@Test
	void notAndMinusExcludeTerms() throws Exception {
		assertThat(ids("java NOT senior")).containsExactly("zq-java-pleno");
		assertThat(ids("java -senior")).containsExactly("zq-java-pleno");
	}

	@Test
	void parenthesesGroupTheExpression() throws Exception {
		// (java OU kotlin) E junior -> apenas a vaga de kotlin junior
		assertThat(ids("(java OR kotlin) junior")).containsExactly("zq-kotlin-junior");
		assertThat(ids("NOT (java OR kotlin) -estagio")).containsExactlyInAnyOrder("zq-dados-senior", "zq-porcento");
	}

	@Test
	void quotedPhraseMustAppearTogether() throws Exception {
		assertThat(ids("\"java senior\"")).containsExactly("zq-java-senior-remoto");
		// Sem as aspas os termos podem estar em campos diferentes; com elas, nao.
		assertThat(ids("\"senior java\"")).isEmpty();
	}

	@Test
	void matchesCompanyLocationSkillsTypeAndLevel() throws Exception {
		assertThat(ids("globex")).containsExactly("zq-java-senior-remoto");        // empresa
		assertThat(ids("recife")).containsExactly("zq-kotlin-junior");             // local
		assertThat(ids("spark")).containsExactly("zq-dados-senior");               // habilidade
		assertThat(ids("estagio")).containsExactly("zq-estagio-front");            // tipo (sem acento)
		assertThat(ids("pleno")).containsExactlyInAnyOrder("zq-java-pleno", "zq-porcento"); // nivel
	}

	@Test
	void searchAndFiltersAreCombined() throws Exception {
		// Antes, informar a busca descartava o nivel escolhido (era um else if em cadeia).
		assertThat(ids("query=java&level=SENIOR")).containsExactly("zq-java-senior-remoto");
		assertThat(ids("remoto&type=PJ")).containsExactly("zq-java-senior-remoto");
		assertThat(ids("remoto&type=CLT&level=PLENO")).containsExactly("zq-porcento");
	}

	@Test
	void skillsFilterIsCombinedWithTheQuery() throws Exception {
		assertThat(ids("query=java&skills=Spring")).containsExactly("zq-java-pleno");
		assertThat(ids("query=java&skills=Kotlin")).containsExactly("zq-java-senior-remoto");
		// Habilidade que nao casa com o texto nao devolve nada.
		assertThat(ids("query=java&skills=React")).isEmpty();
	}

	@Test
	void wildcardsInTheQueryAreLiteral() throws Exception {
		// "%" e "_" sao caracteres de LIKE. Escapados, "%" so casa com o titulo que realmente tem um
		// "%" na frase (o acervo tem "Analista 100% remoto"); se nao fossem escapados, casariam tudo.
		assertThat(ids("%")).containsExactly("zq-porcento");
		assertThat(ids("_")).isEmpty();
		assertThat(ids("100%")).containsExactly("zq-porcento");
	}

	@Test
	void paginationStillWorksWithTheBooleanSearch() throws Exception {
		// Compara apenas o acervo deste teste: outros testes criam vagas no mesmo banco, entao a
		// asserta nao pode depender de quais vagas a primeira pagina pega no total.
		List<String> firstPage = idsFrom(page("developer OR java OR kotlin OR dados", null, null));
		MvcResult first = page("developer OR java OR kotlin OR dados", null, null);
		String cursor = objectMapper.readTree(first.getResponse().getContentAsString()).get("nextCursor").asText();

		List<String> secondPage = idsFrom(page("developer OR java OR kotlin OR dados", cursor, null));

		assertThat(firstPage).isNotEmpty();
		assertThat(secondPage).doesNotContainAnyElementsOf(firstPage);
	}

	private MvcResult page(String query, String cursor, Void ignored) throws Exception {
		var request = get("/jobs").param("query", query).param("limit", "2");
		if (cursor != null) {
			request = request.param("cursor", cursor);
		}
		return mockMvc.perform(request).andExpect(status().isOk()).andReturn();
	}

	private List<String> idsFrom(MvcResult result) throws Exception {
		List<String> ids = new ArrayList<>();
		objectMapper.readTree(result.getResponse().getContentAsString()).get("items")
			.forEach(item -> {
				String id = item.get("id").asText();
				if (id.startsWith(PREFIX + "-")) {
					ids.add(id);
				}
			});
		return ids;
	}

	@Test
	void rejectsAnAbsurdQueryWithoutBreakingTheListing() throws Exception {
		String tooManyTerms = String.join(" ", java.util.Collections.nCopies(70, "termo"));

		mockMvc.perform(get("/jobs").param("query", tooManyTerms))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"));

		// E uma busca normal continua funcionando no mesmo endpoint.
		mockMvc.perform(get("/jobs").param("query", "java")).andExpect(status().isOk());
	}

	private void job(String suffix, String title, String company, String location, String type, String level,
			List<String> skills, Instant postedAt) {
		TestDatabase.insertJob(PREFIX + "-" + suffix, title, company, location, type, level, skills, postedAt);
	}

	/**
	 * Consulta a lista de vagas e devolve so os ids do acervo deste teste.
	 *
	 * Os parametros vao por `.param(...)` (e nao costurados na URL): montando a string, "%20" chegava
	 * literal no `query` e "java senior" virava um termo unico sem espaco.
	 */
	private List<String> ids(String params) throws Exception {
		MockHttpServletRequestBuilder request = get("/jobs").param("limit", "50");
		for (String param : params.split("&")) {
			if (param.isBlank()) {
				continue;
			}
			int separator = param.indexOf('=');
			if (separator > 0) {
				request = request.param(param.substring(0, separator),
					param.substring(separator + 1).replace("%20", " "));
			}
			else {
				request = request.param("query", param.replace("%20", " "));
			}
		}

		MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
		JsonNode items = objectMapper.readTree(result.getResponse().getContentAsString()).get("items");
		List<String> ids = new ArrayList<>();
		items.forEach(item -> {
			String id = item.get("id").asText();
			if (id.startsWith(PREFIX + "-")) {
				ids.add(id);
			}
		});
		return ids;
	}
}