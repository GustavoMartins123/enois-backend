package com.socialcoisa.rede.jobs.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.socialcoisa.rede.jobs.application.port.out.ExternalJobRepository;
import com.socialcoisa.rede.jobs.domain.ExternalJob;
import com.socialcoisa.rede.shared.web.TestDatabase;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * A listagem publica traz as vagas externas (da tabela external_jobs) DEPOIS das internas, marcadas
 * como EXTERNAL e com a URL da origem (para "Visitar vaga"). Interna e externa nao se misturam.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "rede.jobs.import.external-listing-enabled=true")
class ExternalJobsListingTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private ExternalJobRepository externalJobs;

	@Test
	void externalJobsComeAfterInternalOnes() throws Exception {
		String internalId = "job_ext_listing_internal";
		TestDatabase.insertJob(internalId, "Vaga Interna", "Coiso", "Remoto", "FULL_TIME", "MID",
			List.of("Java"), Instant.now().minusSeconds(30));

		externalJobs.save(new ExternalJob("ext_listing_1", "ListagemExterna", "ext-1", "Vaga Externa de Teste",
			null, null, null, null, "https://exemplo.test/vaga/1", Instant.now(), Instant.now(),
			ExternalJob.FOUND));

		MvcResult result = mockMvc.perform(get("/jobs").param("limit", "20"))
			.andExpect(status().isOk()).andReturn();
		JsonNode items = objectMapper.readTree(result.getResponse().getContentAsString()).get("items");

		int internalIndex = -1;
		int externalIndex = -1;
		for (int i = 0; i < items.size(); i++) {
			JsonNode item = items.get(i);
			if (internalId.equals(item.path("id").asString())) internalIndex = i;
			if ("EXTERNAL".equals(item.path("sourceType").asString())) {
				externalIndex = i;
				assertThat(item.path("sourceUrl").asString()).isEqualTo("https://exemplo.test/vaga/1");
				assertThat(item.path("sourcePlatform").asString()).isEqualTo("ListagemExterna");
			}
		}
		assertThat(internalIndex).isGreaterThanOrEqualTo(0);
		assertThat(externalIndex).isGreaterThanOrEqualTo(0);
		assertThat(internalIndex).isLessThan(externalIndex);
	}
}
