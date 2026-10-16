package com.socialcoisa.rede.jobs.adapters.out.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.socialcoisa.rede.shared.web.ApiException;
import tools.jackson.databind.ObjectMapper;

/** Sem a API key do JobRadar, a fonte fica indisponivel e nao busca por caminhos nao autorizados. */
class JobRadarJobSourceTests {

	private final ObjectMapper mapper = new ObjectMapper();

	@Test
	void withoutApiKeyTheSourceIsUnavailable() {
		JobRadarJobSource source = new JobRadarJobSource("https://api.jobradar.com.br", "", "Authorization",
			"Bearer", mapper);

		assertThat(source.name()).isEqualTo("JobRadar");
		assertThat(source.available()).isFalse();
		assertThatThrownBy(() -> source.discover(10))
			.isInstanceOf(ApiException.class)
			.satisfies(e -> {
				assertThat(((ApiException) e).code()).isEqualTo("IMPORT_SOURCE_UNAVAILABLE");
				assertThat(((ApiException) e).status()).isEqualTo(503);
			});
	}

	@Test
	void withApiKeyTheSourceIsAvailable() {
		JobRadarJobSource source = new JobRadarJobSource("https://api.jobradar.com.br", "chave-teste",
			"Authorization", "Bearer", mapper);
		assertThat(source.available()).isTrue();
	}
}
