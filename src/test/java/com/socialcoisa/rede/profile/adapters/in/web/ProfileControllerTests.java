package com.socialcoisa.rede.profile.adapters.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.JsonNode;
import com.socialcoisa.rede.shared.web.TestDatabase;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class ProfileControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	private String sessionToken;
	private String userId;

	@BeforeEach
	void setUp() throws Exception {
		String unique = "prof_" + System.nanoTime();
		String email = unique + "@example.com";
		String signupPayload = objectMapper.writeValueAsString(Map.of(
			"name", "Profile Test User",
			"role", "Engineer",
			"email", email,
			"password", "senha-segura",
			"acceptedTerms", true));

		mockMvc.perform(post("/auth/signup")
				.header("Idempotency-Key", "prof-setup-" + unique)
				.contentType(MediaType.APPLICATION_JSON)
				.content(signupPayload))
			.andExpect(status().isCreated());

		// Conta pendente de confirmacao: confirmamos o e-mail para entrar pelo login normal.
		userId = TestDatabase.userIdByEmail(email).orElseThrow();
		TestDatabase.markEmailVerified(userId, java.time.Instant.now());

		MvcResult login = mockMvc.perform(post("/auth/login")
				.header("Idempotency-Key", "prof-login-" + unique)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("email", email, "password", "senha-segura"))))
			.andExpect(status().isOk())
			.andReturn();

		sessionToken = objectMapper.readTree(login.getResponse().getContentAsString()).get("token").asText();
	}

	@Test
	void rejectsFieldOverTheLimitWithAClearMessage() throws Exception {
		// Bio acima do limite: precisa dizer QUAL campo passou, nao virar um 409 generico (a tela
		// antiga ainda rotulava isso como "nome de usuario ja esta em uso").
		String payload = objectMapper.writeValueAsString(Map.of("bio", "a".repeat(2000)));

		mockMvc.perform(patch("/me/profile")
				.header("X-Session-Token", sessionToken)
				.header("Idempotency-Key", "profile-bio-too-long")
				.contentType(MediaType.APPLICATION_JSON)
				.content(payload))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("FIELD_TOO_LONG"))
			.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("bio")))
			.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("1024")));
	}

	@Test
	void updatesProfile() throws Exception {
		String profilePayload = objectMapper.writeValueAsString(Map.of(
			"headline", "Senior Engineer",
			"company", "Connect Grow",
			"location", "Remoto",
			"bio", "Fullstack developer",
			"skills", List.of("Java", "Spring"),
			"openToWork", true));

		mockMvc.perform(patch("/me/profile")
				.header("X-User-Id", userId)
				.header("X-Session-Token", sessionToken)
				.header("Idempotency-Key", "prof-update-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(profilePayload))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.headline").value("Senior Engineer"))
			.andExpect(jsonPath("$.company").value("Connect Grow"))
			.andExpect(jsonPath("$.skills[0]").value("Java"));

		mockMvc.perform(get("/me")
				.header("X-User-Id", userId)
				.header("X-Session-Token", sessionToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.headline").value("Senior Engineer"));
	}

	@Test
	void managesExperiences() throws Exception {
		String expPayload = objectMapper.writeValueAsString(Map.of(
			"title", "Software Engineer",
			"company", "Tech Co",
			"startDate", "2020-01-01"));

		MvcResult expResult = mockMvc.perform(post("/me/experiences")
				.header("X-User-Id", userId)
				.header("X-Session-Token", sessionToken)
				.header("Idempotency-Key", "prof-exp-create-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(expPayload))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.title").value("Software Engineer"))
			.andReturn();

		String expId = objectMapper.readTree(expResult.getResponse().getContentAsString()).get("id").asText();

		String updateExpPayload = objectMapper.writeValueAsString(Map.of(
			"title", "Senior Software Engineer",
			"company", "Tech Co",
			"startDate", "2020-01-01"));

		mockMvc.perform(put("/me/experiences/{id}", expId)
				.header("X-User-Id", userId)
				.header("X-Session-Token", sessionToken)
				.header("Idempotency-Key", "prof-exp-update-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(updateExpPayload))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("Senior Software Engineer"));

		mockMvc.perform(delete("/me/experiences/{id}", expId)
				.header("X-User-Id", userId)
				.header("X-Session-Token", sessionToken)
				.header("Idempotency-Key", "prof-exp-delete-1"))
			.andExpect(status().isOk());

		mockMvc.perform(get("/me/experiences")
				.header("X-User-Id", userId)
				.header("X-Session-Token", sessionToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isEmpty());
	}

	@Test
	void managesEducation() throws Exception {
		String eduPayload = objectMapper.writeValueAsString(Map.of(
			"school", "USP",
			"degree", "BS",
			"field", "Computer Science",
			"startDate", "2015-01-01",
			"endDate", "2019-12-31"));

		MvcResult eduResult = mockMvc.perform(post("/me/education")
				.header("X-User-Id", userId)
				.header("X-Session-Token", sessionToken)
				.header("Idempotency-Key", "prof-edu-create-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(eduPayload))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.school").value("USP"))
			.andReturn();

		String eduId = objectMapper.readTree(eduResult.getResponse().getContentAsString()).get("id").asText();

		mockMvc.perform(put("/me/education/{id}", eduId)
				.header("X-User-Id", userId)
				.header("X-Session-Token", sessionToken)
				.header("Idempotency-Key", "prof-edu-update-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"school", "USP",
					"degree", "MS",
					"field", "Computer Science",
					"startDate", "2015-01-01",
					"endDate", "2019-12-31"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.degree").value("MS"));

		mockMvc.perform(delete("/me/education/{id}", eduId)
				.header("X-User-Id", userId)
				.header("X-Session-Token", sessionToken)
				.header("Idempotency-Key", "prof-edu-delete-1"))
			.andExpect(status().isOk());
	}

	@Test
	void managesCertifications() throws Exception {
		String certPayload = objectMapper.writeValueAsString(Map.of(
			"name", "AWS Certified",
			"issuer", "Amazon",
			"issueDate", "2023-06-01"));

		MvcResult certResult = mockMvc.perform(post("/me/certifications")
				.header("X-User-Id", userId)
				.header("X-Session-Token", sessionToken)
				.header("Idempotency-Key", "prof-cert-create-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(certPayload))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.name").value("AWS Certified"))
			.andReturn();

		String certId = objectMapper.readTree(certResult.getResponse().getContentAsString()).get("id").asText();

		mockMvc.perform(put("/me/certifications/{id}", certId)
				.header("X-User-Id", userId)
				.header("X-Session-Token", sessionToken)
				.header("Idempotency-Key", "prof-cert-update-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"name", "AWS Certified Solutions Architect",
					"issuer", "Amazon",
					"issueDate", "2023-06-01"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("AWS Certified Solutions Architect"));

		mockMvc.perform(delete("/me/certifications/{id}", certId)
				.header("X-User-Id", userId)
				.header("X-Session-Token", sessionToken)
				.header("Idempotency-Key", "prof-cert-delete-1"))
			.andExpect(status().isOk());
	}

	@Test
	void viewsPublicProfile() throws Exception {
		mockMvc.perform(get("/me")
				.header("X-User-Id", userId)
				.header("X-Session-Token", sessionToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.username").isNotEmpty());
	}

	@Test
	void rankingEOpcionalPorPreferenciaDaConta() throws Exception {
		// Padrao: mostra (compatibilidade com quem ja usava).
		mockMvc.perform(get("/me").header("X-Session-Token", sessionToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.showRanking").value(true));

		mockMvc.perform(put("/me/ranking")
				.header("X-Session-Token", sessionToken)
				.header("Idempotency-Key", "ranking-off-" + userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("show", false))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("RANKING_HIDDEN"));

		mockMvc.perform(get("/me").header("X-Session-Token", sessionToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.showRanking").value(false));

		mockMvc.perform(put("/me/ranking")
				.header("X-Session-Token", sessionToken)
				.header("Idempotency-Key", "ranking-on-" + userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("show", true))))
			.andExpect(status().isOk());

		mockMvc.perform(get("/me").header("X-Session-Token", sessionToken))
			.andExpect(jsonPath("$.showRanking").value(true));
	}
}