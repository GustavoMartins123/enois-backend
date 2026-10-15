package com.socialcoisa.rede.jobs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.socialcoisa.rede.jobs.application.port.out.JobRepository;
import com.socialcoisa.rede.jobs.domain.Job;
import com.socialcoisa.rede.profile.application.ReputationService;
import com.socialcoisa.rede.shared.web.ApiException;

@ExtendWith(MockitoExtension.class)
class JobServiceTests {

	private static final String USER = "user_job_tests";

	@Mock
	private JobRepository repository;

	@Mock
	private ReputationService reputationService;

	@Mock
	private com.socialcoisa.rede.profile.application.port.out.UserProfileRepository userProfileRepository;

	@Mock
	private com.socialcoisa.rede.profile.application.port.out.ProfileExtensionRepository extensionRepository;

	@Mock
	private com.socialcoisa.rede.jobs.application.port.out.JobApplicationDelivery delivery;

	private JobService service;

	@BeforeEach
	void setUp() {
		service = new JobService(repository, reputationService, userProfileRepository, extensionRepository, delivery,
			new tools.jackson.databind.ObjectMapper(), Clock.systemUTC());
	}

	@Test
	void normalizesCurrencySymbolOutOfTheSalaryAndKeepsTheChosenCurrency() {
		when(reputationService.reputationOf(USER)).thenReturn(300);

		Job job = service.create("Pessoa Desenvolvedora", "Acme", null, "Remoto", "CLT", "PLENO",
			"R$ 8.000 – R$ 12.000", "brl", List.of("Java"), List.of(), USER, true);

		assertThat(job.salary()).isEqualTo("8.000 – 12.000");
		assertThat(job.currency()).isEqualTo("BRL");
		assertThat(job.communityAgreedAt()).isNotNull();
	}

	@Test
	void dollarSalaryIsAlsoNormalized() {
		when(reputationService.reputationOf(USER)).thenReturn(300);

		Job job = service.create("Backend Engineer", "Acme", null, "Remote", "PJ", "SENIOR",
			"US$ 90,000/year", "usd", List.of("Go"), List.of(), USER, true);

		assertThat(job.salary()).isEqualTo("90,000/year");
		assertThat(job.currency()).isEqualTo("USD");
	}

	@Test
	void defaultsToBrlWhenCurrencyIsOmitted() {
		when(reputationService.reputationOf(USER)).thenReturn(300);

		Job job = service.create("Suporte", "Acme", null, "São Paulo", "CLT", "JUNIOR", null, null, List.of(),
			List.of(), USER, true);

		assertThat(job.currency()).isEqualTo("BRL");
		assertThat(job.salary()).isNull();
	}

	@Test
	void refusesToPublishWithoutTheCommunityAgreement() {
		when(reputationService.reputationOf(USER)).thenReturn(300);

		assertThatThrownBy(() -> service.create("Vaga", "Acme", null, null, "CLT", "PLENO", null, "BRL", List.of(),
			List.of(), USER, false))
			.isInstanceOf(ApiException.class)
			.hasMessageContaining("regras da comunidade");
	}

	@Test
	void refusesUnsupportedCurrency() {
		when(reputationService.reputationOf(USER)).thenReturn(300);

		assertThatThrownBy(() -> service.create("Vaga", "Acme", null, null, "CLT", "PLENO", "1000", "BTC", List.of(),
			List.of(), USER, true))
			.isInstanceOf(ApiException.class)
			.hasMessageContaining("Moeda nao suportada");
	}

	@Test
	void keepsTheReputationGate() {
		when(reputationService.reputationOf(USER)).thenReturn(JobService.MIN_REPUTATION_TO_CREATE_JOB);

		assertThatThrownBy(() -> service.create("Vaga", "Acme", null, null, "CLT", "PLENO", null, "BRL", List.of(),
			List.of(), USER, true))
			.isInstanceOf(ApiException.class)
			.hasMessageContaining("reputacao");
	}
}