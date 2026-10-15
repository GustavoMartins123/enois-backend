package com.socialcoisa.rede.jobs.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.socialcoisa.rede.identity.application.EmailService;
import com.socialcoisa.rede.profile.application.ReputationService;
import com.socialcoisa.rede.shared.web.TestAuthHelper;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Candidatura de ponta a ponta: formulario montado por quem publica (campos obrigatorios e opcionais),
 * validacao, retrato do candidato, entrega por e-mail e DM, e a lista que o empregador ve.
 */
@SpringBootTest
@AutoConfigureMockMvc
class JobApplicationFlowTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	/** A porta de reputacao sustenta o gate de publicacao; aqui basta um valor alto e estavel. */
	@MockitoBean
	private ReputationService reputationService;

	/** Espiao do e-mail: a candidatura precisa chegar ao empregador por aqui tambem. */
	@MockitoBean
	private EmailService emailService;

	private final List<String> sentSubjects = new ArrayList<>();
	private final List<String> sentBodies = new ArrayList<>();

	private TestAuthHelper auth;

	@BeforeEach
	void setUp() {
		sentSubjects.clear();
		sentBodies.clear();
		when(reputationService.reputationOf(anyString())).thenReturn(300);
		when(emailService.sendHtml(anyString(), anyString(), anyString())).thenAnswer(invocation -> {
			sentSubjects.add(invocation.getArgument(1, String.class));
			sentBodies.add(invocation.getArgument(2, String.class));
			return true;
		});
		auth = new TestAuthHelper(mockMvc, objectMapper);
	}

	@Test
	void employerDefinesFieldsAndReceivesTheApplicationWithTheCandidateProfile() throws Exception {
		var employer = auth.authenticate("cand_employer");
		var candidate = auth.authenticate("cand_candidate");
		// Duas experiencias: a primeira comeca 6 anos atras (o "tempo de xp" do retrato vem daqui).
		addExperience(candidate, "Desenvolvedora Back-end", java.time.LocalDate.now().minusYears(6));
		addExperience(candidate, "Desenvolvedora Junior", java.time.LocalDate.now().minusYears(2));

		String jobId = createJob(employer, """
				[{"label":"Anos de experiência com Java","type":"NUMBER","required":true},
				 {"label":"Link do portfólio","type":"LINK","required":false},
				 {"label":"Por que essa vaga?","type":"TEXTAREA","required":false}]""");

		// O candidato ve o formulario e quem publicou (credibilidade de quem esta do outro lado).
		mockMvc.perform(get("/jobs/" + jobId).header("X-Session-Token", candidate.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.applicationFields.length()").value(3))
			.andExpect(jsonPath("$.applicationFields[0].label").value("Anos de experiência com Java"))
			.andExpect(jsonPath("$.applicationFields[0].required").value(true))
			.andExpect(jsonPath("$.author.username").value("cand-employer"))
			.andExpect(jsonPath("$.author.identityVerified").value(true));

		// Campo obrigatorio sem resposta barra a candidatura, dizendo qual falta.
		mockMvc.perform(post("/jobs/" + jobId + "/applications")
				.header("X-Session-Token", candidate.token())
				.header("Idempotency-Key", "cand-missing")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("message", "Tenho interesse", "answers", List.of()))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("MISSING_REQUIRED_ANSWERS"))
			.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Anos de experiência")));

		// Valor que nao combina com o tipo do campo tambem barra.
		mockMvc.perform(post("/jobs/" + jobId + "/applications")
				.header("X-Session-Token", candidate.token())
				.header("Idempotency-Key", "cand-invalid")
				.contentType(MediaType.APPLICATION_JSON)
				.content(answersBody(jobId, candidate, List.of("varios anos"))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_ANSWER"));

		// Candidatura valida.
		MvcResult applied = mockMvc.perform(post("/jobs/" + jobId + "/applications")
				.header("X-Session-Token", candidate.token())
				.header("Idempotency-Key", "cand-ok")
				.contentType(MediaType.APPLICATION_JSON)
				.content(answersBody(jobId, candidate, java.util.Arrays.asList("6", null, "Porque quero crescer"))))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("APPLIED"))
			.andExpect(jsonPath("$.answersCount").value(2))
			.andReturn();
		assertThat(applied.getResponse().getContentAsString()).contains("\"id\"");

		// O empregador recebe por e-mail, com o perfil e as respostas.
		assertThat(sentSubjects).hasSize(1);
		assertThat(sentSubjects.get(0)).contains("Nova candidatura");
		assertThat(sentBodies.get(0)).contains("cand_candidate").contains("Anos de experiência com Java")
			.contains("Por que essa vaga?").contains("Reputação: 300").contains("conta verificada")
			.contains("Experiência: 6 anos (2 experiências)");

		// ... e na mensagem direta, criada na hora entre os dois.
		MvcResult conversations = mockMvc.perform(get("/conversations").header("X-Session-Token", employer.token()))
			.andExpect(status().isOk())
			.andReturn();
		JsonNode items = objectMapper.readTree(conversations.getResponse().getContentAsString()).get("items");
		assertThat(items.size()).isEqualTo(1);
		String conversationId = items.get(0).get("id").asText();

		MvcResult messages = mockMvc.perform(get("/conversations/" + conversationId + "/messages")
				.header("X-Session-Token", employer.token()))
			.andExpect(status().isOk())
			.andReturn();
		assertThat(messages.getResponse().getContentAsString())
			.contains("Candidatura para")
			.contains("cand_candidate")
			.contains("Experiência: 6 anos (2 experiências)")
			.contains("Anos de experiência com Java: 6")
			.contains("Por que essa vaga?: Porque quero crescer");

		// O empregador ve a lista de candidaturas com o retrato do candidato.
		mockMvc.perform(get("/jobs/" + jobId + "/applications").header("X-Session-Token", employer.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].candidate.username").value("cand-candidate"))
			.andExpect(jsonPath("$[0].candidate.reputation").value(300))
			.andExpect(jsonPath("$[0].candidate.experienceYears").value(6))
			.andExpect(jsonPath("$[0].candidate.experiencesCount").value(2))
			.andExpect(jsonPath("$[0].message").value("Quero muito essa vaga"))
			.andExpect(jsonPath("$[0].answers.length()").value(2));

		// Candidato nao ve a lista de quem se candidatou (nem o total).
		mockMvc.perform(get("/jobs/" + jobId + "/applications").header("X-Session-Token", candidate.token()))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("NOT_OWNER"));

		// Candidatar de novo e bloqueado com motivo claro (antes a tela so dizia "erro inesperado").
		mockMvc.perform(post("/jobs/" + jobId + "/applications")
				.header("X-Session-Token", candidate.token())
				.header("Idempotency-Key", "cand-again")
				.contentType(MediaType.APPLICATION_JSON)
				.content(answersBody(jobId, candidate, java.util.Arrays.asList("6", null, "Porque quero crescer"))))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ALREADY_APPLIED"));
	}

	@Test
	void ownerCannotApplyToOwnJobAndOptionalFieldsMayBeLeftEmpty() throws Exception {
		var employer = auth.authenticate("cand_owner");
		var candidate = auth.authenticate("cand_simple");

		String jobId = createJob(employer, """
				[{"label":"Anos de experiência","type":"NUMBER","required":false}]""");

		mockMvc.perform(post("/jobs/" + jobId + "/applications")
				.header("X-Session-Token", employer.token())
				.header("Idempotency-Key", "cand-owner")
				.contentType(MediaType.APPLICATION_JSON)
				.content(answersBody(jobId, employer, List.of("10"))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("CANNOT_APPLY_TO_OWN_JOB"));

		// Opcional em branco nao atrapalha.
		mockMvc.perform(post("/jobs/" + jobId + "/applications")
				.header("X-Session-Token", candidate.token())
				.header("Idempotency-Key", "cand-optional")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("answers", List.of()))))
			.andExpect(status().isCreated());
	}

	@Test
	void formRejectsDuplicateLabelsAndAbsurdFieldCounts() throws Exception {
		var employer = auth.authenticate("cand_limits");

		mockMvc.perform(post("/jobs")
				.header("X-Session-Token", employer.token())
				.header("Idempotency-Key", "cand-dup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(jobBody("""
					[{"label":"Experiência","type":"TEXT","required":true},
					 {"label":"experiência","type":"TEXT","required":false}]""")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("DUPLICATE_FIELD_LABEL"));

		mockMvc.perform(post("/jobs")
				.header("X-Session-Token", employer.token())
				.header("Idempotency-Key", "cand-type")
				.contentType(MediaType.APPLICATION_JSON)
				.content(jobBody("""
					[{"label":"Currículo","type":"PDF","required":true}]""")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_FIELD_TYPE"));
	}

	/** Registra uma experiencia no perfil do candidato (o retrato usa a mais antiga para os anos). */
	private void addExperience(TestAuthHelper.AuthSession session, String title, java.time.LocalDate startDate)
			throws Exception {
		mockMvc.perform(post("/me/experiences")
				.header("X-Session-Token", session.token())
				.header("Idempotency-Key", "cand-exp-" + title)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"title", title, "company", "Empresa", "location", "Remoto", "description", "x",
					"startDate", startDate.toString()))))
			.andExpect(status().isCreated());
	}

	private String createJob(TestAuthHelper.AuthSession session, String fieldsJson) throws Exception {
		MvcResult result = mockMvc.perform(post("/jobs")
				.header("X-Session-Token", session.token())
				.header("Idempotency-Key", "cand-job-" + session.userId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(jobBody(fieldsJson)))
			.andExpect(status().isCreated())
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
	}

	private String jobBody(String fieldsJson) throws Exception {
		return """
				{"title":"Pessoa Desenvolvedora","company":"Acme","location":"Remoto","type":"CLT","level":"PLENO",
				 "currency":"BRL","skills":["Java"],"communityAgreement":true,"applicationFields":%s}"""
			.formatted(fieldsJson);
	}

	/** Ids reais dos campos da vaga: a resposta aponta para eles, como no app. */
	private List<String> fieldIds(String jobId, TestAuthHelper.AuthSession session) throws Exception {
		MvcResult result = mockMvc.perform(get("/jobs/" + jobId).header("X-Session-Token", session.token()))
			.andExpect(status().isOk())
			.andReturn();
		List<String> ids = new ArrayList<>();
		objectMapper.readTree(result.getResponse().getContentAsString()).get("applicationFields")
			.forEach(field -> ids.add(field.get("id").asText()));
		return ids;
	}

	/** Corpo da candidatura com a mensagem e as respostas na ordem dos campos da vaga. */
	private String answersBody(String jobId, TestAuthHelper.AuthSession session, List<String> values)
			throws Exception {
		List<String> ids = fieldIds(jobId, session);
		List<Map<String, String>> answers = new ArrayList<>();
		for (int index = 0; index < values.size() && index < ids.size(); index++) {
			if (values.get(index) != null) {
				answers.add(Map.of("fieldId", ids.get(index), "value", values.get(index)));
			}
		}
		return objectMapper.writeValueAsString(Map.of("message", "Quero muito essa vaga", "answers", answers));
	}
}