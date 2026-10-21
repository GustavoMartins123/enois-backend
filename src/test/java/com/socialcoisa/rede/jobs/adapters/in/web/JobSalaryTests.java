package com.socialcoisa.rede.jobs.adapters.in.web;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.ObjectMapper;
import com.socialcoisa.rede.profile.application.ReputationService;
import com.socialcoisa.rede.shared.web.TestAuthHelper;

/**
 * Item 5 do backlog: a vaga NOVA precisa dizer o que é (descrição) e quanto paga
 * (valor mínimo > 0), com faixa opcional coerente e período do valor. Sem isso não
 * salva. Vagas antigas (texto livre) continuam sendo exibidas como sempre foram.
 */
@SpringBootTest
@AutoConfigureMockMvc
class JobSalaryTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private ReputationService reputationService;

	private TestAuthHelper auth;

	@BeforeEach
	void setUp() {
		auth = new TestAuthHelper(mockMvc, objectMapper);
		when(reputationService.reputationOf(anyString())).thenReturn(300);
	}

	private String validBody(String extra) {
		String base = """
				{"title":"Backend","company":"Acme","location":"Remoto","type":"CLT","level":"PLENO",
				 "currency":"BRL","salaryMin":"1500","salaryPeriod":"MONTH",
				 "description":"Manutenção do legado e café.","skills":["Java"],
				 "communityAgreement":true,"applicationFields":[]}%s""";
		return base.formatted(extra == null ? "" : extra);
	}

	private TestAuthHelper.AuthSession employer() throws Exception {
		return auth.authenticate("salary_employer");
	}

	@Test
	void jobWithoutDescriptionIsRejected() throws Exception {
		var employer = employer();
		String body = validBody(null).replace("\"description\":\"Manutenção do legado e café.\",", "");
		mockMvc.perform(post("/jobs")
				.header("X-Session-Token", employer.token())
				.header("Idempotency-Key", "sal-no-desc")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("DESCRIPTION_REQUIRED"));
	}

	@Test
	void jobWithoutValueIsRejected() throws Exception {
		var employer = employer();
		String body = validBody(null).replace("\"salaryMin\":\"1500\",", "");
		mockMvc.perform(post("/jobs")
				.header("X-Session-Token", employer.token())
				.header("Idempotency-Key", "sal-no-value")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("SALARY_REQUIRED"));
	}

	@Test
	void zeroOrNegativeValueIsRejected() throws Exception {
		var employer = employer();
		String body = validBody(null).replace("\"salaryMin\":\"1500\"", "\"salaryMin\":\"0\"");
		mockMvc.perform(post("/jobs")
				.header("X-Session-Token", employer.token())
				.header("Idempotency-Key", "sal-zero")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("SALARY_REQUIRED"));
	}

	@Test
	void invertedRangeIsRejected() throws Exception {
		var employer = employer();
		String body = validBody(null).replace("}", ",\"salaryMax\":\"1200\"}");
		mockMvc.perform(post("/jobs")
				.header("X-Session-Token", employer.token())
				.header("Idempotency-Key", "sal-range")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_SALARY_RANGE"));
	}

	@Test
	void invalidPeriodIsRejected() throws Exception {
		var employer = employer();
		String body = validBody(null).replace("\"salaryPeriod\":\"MONTH\"", "\"salaryPeriod\":\"WEEK\"");
		mockMvc.perform(post("/jobs")
				.header("X-Session-Token", employer.token())
				.header("Idempotency-Key", "sal-period")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_SALARY_PERIOD"));
	}

	@Test
	void validJobIsCreatedWithBrazilianAmountAndLabel() throws Exception {
		var employer = employer();
		// "1.500" em português é mil e quinhentos; com faixa até "2000".
		String body = validBody(null)
			.replace("\"salaryMin\":\"1500\"", "\"salaryMin\":\"1.500\"")
			.replace("}", ",\"salaryMax\":\"2000\"}");
		MvcResult result = mockMvc.perform(post("/jobs")
				.header("X-Session-Token", employer.token())
				.header("Idempotency-Key", "sal-ok")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.salaryLabel").value("de R$ 1.500 até R$ 2.000 / mês"))
			.andExpect(jsonPath("$.salaryPeriod").value("MONTH"))
			.andExpect(jsonPath("$.description").value("Manutenção do legado e café."))
			.andReturn();
		String jobId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

		// A página da vaga (leitura pública) mostra o mesmo rótulo.
		mockMvc.perform(get("/jobs/" + jobId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.salaryLabel").value("de R$ 1.500 até R$ 2.000 / mês"))
			.andExpect(jsonPath("$.description").value("Manutenção do legado e café."));
	}

	@Test
	void editingAJobKeepsTheRules() throws Exception {
		var employer = employer();
		MvcResult result = mockMvc.perform(post("/jobs")
				.header("X-Session-Token", employer.token())
				.header("Idempotency-Key", "sal-edit-create")
				.contentType(MediaType.APPLICATION_JSON)
				.content(validBody(null)))
			.andExpect(status().isCreated())
			.andReturn();
		String jobId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

		// Editar sem o valor obrigatório: recusado (a regra vale na edição também).
		String noValue = validBody(null).replace("\"salaryMin\":\"1500\",", "");
		mockMvc.perform(put("/jobs/" + jobId)
				.header("X-Session-Token", employer.token())
				.header("Idempotency-Key", "sal-edit-no-value")
				.contentType(MediaType.APPLICATION_JSON)
				.content(noValue))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("SALARY_REQUIRED"));

		// Editar mantendo a regra: ok, e o rótulo acompanha a nova faixa.
		mockMvc.perform(put("/jobs/" + jobId)
				.header("X-Session-Token", employer.token())
				.header("Idempotency-Key", "sal-edit-ok")
				.contentType(MediaType.APPLICATION_JSON)
				.content(validBody(null).replace("\"salaryMin\":\"1500\"", "\"salaryMin\":\"2500,50\"")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.salaryLabel").value("R$ 2.500,50 / mês"));
	}
}
