package com.socialcoisa.rede.jobs.adapters.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.ObjectMapper;
import com.socialcoisa.rede.shared.web.TestAuthHelper;
import com.socialcoisa.rede.shared.web.TestDatabase;

/**
 * O dono da vaga nunca pode aparecer como "Inscrito": o apply bloqueia candidatar-se a propria vaga,
 * mas uma candidatura orfa (criada antes da regra) marcava o dono como applied.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OwnJobAppliedFlagTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	private TestAuthHelper auth;

	@BeforeEach
	void setUp() {
		auth = new TestAuthHelper(mockMvc, objectMapper);
	}

	@Test
	void ownerIsNeverAppliedEvenWithOrphanApplication() throws Exception {
		var owner = auth.authenticate("ownjob_owner");
		String jobId = createJob(owner);

		// Candidatura orfa: insere direto no banco (o endpoint bloquearia).
		TestDatabase.insertJobApplication(jobId, owner.userId());

		mockMvc.perform(get("/jobs/" + jobId).header("X-Session-Token", owner.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.owner").value(true))
			.andExpect(jsonPath("$.applied").value(false));
	}

	private String createJob(TestAuthHelper.AuthSession owner) throws Exception {
		String jobId = "job_ownjob_" + owner.userId();
		TestDatabase.insertJob(jobId, "Vaga do proprio dono", "Coiso", "Remoto", "FULL_TIME", "MID",
			List.of("Java"), java.time.Instant.now());
		TestDatabase.setJobPostedBy(jobId, owner.userId());
		return jobId;
	}
}
