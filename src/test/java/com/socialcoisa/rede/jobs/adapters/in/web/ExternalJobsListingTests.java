package com.socialcoisa.rede.jobs.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.socialcoisa.rede.jobs.application.port.out.JobImportSource;
import com.socialcoisa.rede.shared.web.TestDatabase;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Listagem de vagas externas: entram DEPOIS das internas, marcadas como EXTERNAL e com a URL da
 * origem (para o "Visitar vaga"/redirecionamento).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
	"rede.jobs.import.external-listing-enabled=true",
	// Desliga a fonte real (sitemap do JobRadar) para o teste ser offline e deterministico.
	"rede.jobs.import.jobradar.enabled=false"
})
class ExternalJobsListingTests {

	@TestConfiguration
	static class StubSourceConfig {
		@Bean
		JobImportSource stubSource() {
			return new JobImportSource() {
				@Override public String name() { return "StubSource"; }
				@Override public boolean available() { return true; }
				@Override public List<ExternalJob> discover(int limit) {
					return List.of(new ExternalJob("ext-1", "Vaga Externa de Teste", null, null, null, null,
						null, "https://exemplo.test/vaga/1", "https://exemplo.test/vaga/1", name(), null));
				}
			};
		}
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void externalJobsComeAfterInternalOnes() throws Exception {
		String internalId = "job_ext_listing_internal";
		TestDatabase.insertJob(internalId, "Vaga Interna", "Coiso", "Remoto", "FULL_TIME", "MID",
			List.of("Java"), Instant.now().minusSeconds(30));

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
				assertThat(item.path("sourcePlatform").asString()).isEqualTo("StubSource");
			}
		}
		assertThat(internalIndex).isGreaterThanOrEqualTo(0);
		assertThat(externalIndex).isGreaterThanOrEqualTo(0);
		// Internas primeiro.
		assertThat(internalIndex).isLessThan(externalIndex);
	}
}
