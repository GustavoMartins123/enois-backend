package com.socialcoisa.rede.jobs.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;

import com.socialcoisa.rede.jobs.application.port.out.ExternalJobRepository;
import com.socialcoisa.rede.jobs.application.port.out.JobImportSource;

/**
 * Deduplicacao GLOBAL entre fontes: a MESMA vaga publicada em duas plataformas (mesma URL canonica,
 * id externo diferente) nao pode virar duas linhas. A assercao nao depende da ordem das fontes.
 */
@SpringBootTest
@TestPropertySource(properties = {
	"rede.jobs.import.jobradar.enabled=false",
	// Desliga o seed/cron: no teste o refresh e chamado explicitamente (evita corrida paralela).
	"rede.jobs.import.external-refresh-enabled=false"
})
class GlobalDedupeTests {

	private static final String SHARED = "https://exemplo.test/vaga/compartilhada";

	@TestConfiguration
	static class TwoSourcesConfig {
		@Bean
		JobImportSource fonteA() {
			return fixed("FonteA", "a-1", SHARED);
		}

		@Bean
		JobImportSource fonteB() {
			// Mesma vaga, com parametro de rastreio (a canonicalizacao remove utm_*) e id diferente.
			return fixed("FonteB", "b-1", SHARED + "?utm_source=agregador");
		}

		private static JobImportSource fixed(String name, String externalId, String url) {
			return new JobImportSource() {
				@Override public String name() { return name; }
				@Override public boolean available() { return true; }
				@Override public List<ExternalJob> discover(int limit) {
					return List.of(new ExternalJob(externalId, "Desenvolvedor Java", null, null, null, null,
						null, url, url, name(), null));
				}
			};
		}
	}

	@Autowired
	private ExternalJobImportService importService;

	@Autowired
	private ExternalJobRepository repository;

	@Test
	void theSameJobFromTwoSourcesIsStoredOnce() {
		importService.refresh();

		long rowsForThisJob = repository.findRecent(500).stream()
			.filter(job -> SHARED.equals(job.canonicalUrl()))
			.count();
		assertThat(rowsForThisJob).isEqualTo(1);
		assertThat(repository.findByCanonicalUrl(SHARED)).isPresent();
	}
}
