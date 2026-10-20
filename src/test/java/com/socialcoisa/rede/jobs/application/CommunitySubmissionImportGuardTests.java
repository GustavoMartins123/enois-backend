package com.socialcoisa.rede.jobs.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;

import com.socialcoisa.rede.jobs.application.port.out.ExternalJobRepository;
import com.socialcoisa.rede.jobs.application.port.out.JobImportSource;
import com.socialcoisa.rede.jobs.domain.ExternalJob;

/**
 * A vaga cadastrada pela COMUNIDADE e a fonte da verdade do proprio registro: quando um agregador
 * publica a mesma vaga (mesma URL canonica), a importacao nao reescreve titulo/empresa nem assume
 * a procedencia — senao o cadastro da pessoa "virava" uma importada e ninguem poderia excluir.
 */
@SpringBootTest
@TestPropertySource(properties = {
	"rede.jobs.import.jobradar.enabled=false",
	"rede.jobs.import.external-refresh-enabled=false"
})
class CommunitySubmissionImportGuardTests {

	private static final String URL = "https://exemplo.test/vaga/cadastrada-pela-comunidade";

	@TestConfiguration
	static class CommunitySourceConfig {
		@Bean
		JobImportSource fonteComunidade() {
			return new JobImportSource() {
				@Override public String name() { return "FonteGuard"; }
				@Override public boolean available() { return true; }
				@Override public List<ExternalJob> discover(int limit) {
					// Mesma vaga, OUTRA plataforma e outro id: cai na dedupe pela URL canonica.
					return List.of(new ExternalJob("guard-1", "Vaga importada de teste", "Outra Empresa",
						null, null, null, null, URL + "?utm_source=agregador", URL + "?utm_source=agregador",
						"FonteGuard", null));
				}
			};
		}
	}

	@Autowired
	private ExternalJobImportService importService;

	@Autowired
	private ExternalJobRepository repository;

	@Test
	void importDoesNotOverwriteNorDuplicateACommunitySubmission() {
		Instant now = Instant.now();
		repository.save(new ExternalJob("ext_com_guard", "Comunidade", "community_guard",
			"Vaga cadastrada pela comunidade", "Empresa da comunidade", null, null, null, URL, now, now,
			ExternalJob.FOUND, URL, null, "user_guard"));

		importService.refresh();

		ExternalJob row = repository.findByCanonicalUrl(URL).orElseThrow();
		assertThat(row.platform()).isEqualTo("Comunidade");
		assertThat(row.submittedBy()).isEqualTo("user_guard");
		assertThat(row.title()).isEqualTo("Vaga cadastrada pela comunidade");
		assertThat(row.company()).isEqualTo("Empresa da comunidade");
		// E nem linha duplicada: a guarda e "nao toca", nao "atualiza e mantem o autor".
		long rows = repository.findRecent(5000).stream()
			.filter(job -> URL.equals(job.canonicalUrl()))
			.count();
		assertThat(rows).isEqualTo(1);
	}
}
