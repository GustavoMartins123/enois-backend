package com.socialcoisa.rede.jobs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;

import com.socialcoisa.rede.jobs.application.port.out.ExternalJobRepository;
import com.socialcoisa.rede.jobs.domain.ExternalJob;
import com.socialcoisa.rede.profile.application.ReputationService;
import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Regras do cadastro MANUAL de vaga externa: gate de reputacao, declaracao da comunidade, link
 * https de site real e dedupe pela URL canonica (a mesma que a importacao ja usa).
 */
@ExtendWith(MockitoExtension.class)
class ExternalJobSubmissionServiceTests {

	private static final String USER = "user_external_submission";
	private static final String OTHER = "user_external_other";

	@Mock
	private ExternalJobRepository repository;

	@Mock
	private ReputationService reputationService;

	@Mock
	private CacheManager cacheManager;

	private ExternalJobSubmissionService service;

	@BeforeEach
	void setUp() {
		service = new ExternalJobSubmissionService(repository, reputationService, cacheManager, Clock.systemUTC());
	}

	@Test
	void createsCommunitySubmissionWithTheAuthorAndTheCanonicalUrl() {
		when(reputationService.reputationOf(USER)).thenReturn(300);

		ExternalJob job = service.submit(USER, "  Desenvolvedora   Back-end ", "Acme", "Remoto",
			"https://acme.test/vagas/1?utm_source=linkedin", true);

		assertThat(job.id()).startsWith("ext_com_");
		assertThat(job.platform()).isEqualTo(ExternalJobSubmissionService.COMMUNITY_PLATFORM);
		assertThat(job.submittedBy()).isEqualTo(USER);
		assertThat(job.isCommunitySubmission()).isTrue();
		assertThat(job.status()).isEqualTo(ExternalJob.FOUND);
		// Espacos colapsados e parametro de rastreio fora: a canonica e a chave da dedupe.
		assertThat(job.title()).isEqualTo("Desenvolvedora Back-end");
		assertThat(job.canonicalUrl()).isEqualTo("https://acme.test/vagas/1");
		verify(repository).save(job);
	}

	@Test
	void keepsTheSameReputationGateAsTheHouseJob() {
		when(reputationService.reputationOf(USER)).thenReturn(JobService.MIN_REPUTATION_TO_CREATE_JOB);

		assertThatThrownBy(() -> service.submit(USER, "Vaga", "Acme", null, "https://acme.test/1", true))
			.isInstanceOf(ApiException.class)
			.satisfies(exception -> assertThat(((ApiException) exception).code()).isEqualTo("LOW_REPUTATION"));
		verifyNoInteractions(repository);
	}

	@Test
	void refusesWithoutTheCommunityAgreement() {
		when(reputationService.reputationOf(USER)).thenReturn(300);

		assertThatThrownBy(() -> service.submit(USER, "Vaga", "Acme", null, "https://acme.test/1", false))
			.isInstanceOf(ApiException.class)
			.satisfies(exception -> assertThat(((ApiException) exception).code())
				.isEqualTo("COMMUNITY_RULES_NOT_ACCEPTED"));
		verifyNoInteractions(repository);
	}

	@Test
	void refusesDuplicateByCanonicalUrl() {
		when(reputationService.reputationOf(USER)).thenReturn(300);
		when(repository.findByCanonicalUrl("https://acme.test/vagas/1"))
			.thenReturn(Optional.of(new ExternalJob("ext_ja_existe", "Remotive", "1", "Backend", "Acme", null, null,
				null, "https://acme.test/vagas/1", java.time.Instant.now(), java.time.Instant.now(),
				ExternalJob.FOUND, "https://acme.test/vagas/1", null)));

		// A mesma vaga com rastreio diferente: a canonica derruba o duplicado.
		assertThatThrownBy(() -> service.submit(USER, "Desenvolvedora Back-end", "Acme", null,
			"https://acme.test/vagas/1?ref=indicacao", true))
			.isInstanceOf(ApiException.class)
			.satisfies(exception -> assertThat(((ApiException) exception).code()).isEqualTo("JOB_ALREADY_LISTED"));
	}

	@Test
	void refusesLinksThatAreNotHttpsOnARealSite() {
		// Sem stub de reputacao: a validacao do link acontece ANTES do gate (nao chega a consultar).
		for (String url : new String[] { "http://acme.test/vaga", "javascript:alert(1)", "ftp://acme.test/vaga",
				"https://usuario@malicioso.test/vaga", "sem-endereco" }) {
			assertThatThrownBy(() -> service.submit(USER, "Vaga", "Acme", null, url, true))
				.isInstanceOf(ApiException.class)
				.satisfies(exception -> assertThat(((ApiException) exception).code())
					.isEqualTo("INVALID_EXTERNAL_JOB"));
		}
		verifyNoInteractions(repository);
	}

	@Test
	void refusesBlankTitleOrCompany() {
		// Sem stub de reputacao: titulo/empresa sao validados antes do gate.
		assertThatThrownBy(() -> service.submit(USER, "   ", "Acme", null, "https://acme.test/1", true))
			.isInstanceOf(ApiException.class);
		assertThatThrownBy(() -> service.submit(USER, "Vaga", null, null, "https://acme.test/1", true))
			.isInstanceOf(ApiException.class);
		verifyNoInteractions(repository);
	}

	@Test
	void onlyTheAuthorDeletesTheSubmission() {
		ExternalJob mine = new ExternalJob("ext_com_1", ExternalJobSubmissionService.COMMUNITY_PLATFORM, "community_1",
			"Backend", "Acme", null, null, null, "https://acme.test/vaga", java.time.Instant.now(),
			java.time.Instant.now(), ExternalJob.FOUND, "https://acme.test/vaga", null, USER);
		when(repository.findById("ext_com_1")).thenReturn(Optional.of(mine));

		assertThatThrownBy(() -> service.remove(OTHER, "ext_com_1"))
			.isInstanceOf(ApiException.class)
			.satisfies(exception -> assertThat(((ApiException) exception).code()).isEqualTo("NOT_OWNER"));

		service.remove(USER, "ext_com_1");
		verify(repository).delete("ext_com_1");
	}

	@Test
	void deletingAMissingSubmissionIs404() {
		when(repository.findById("ext_com_nada")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.remove(USER, "ext_com_nada"))
			.isInstanceOf(ApiException.class)
			.satisfies(exception -> assertThat(((ApiException) exception).status()).isEqualTo(404));
	}
}
