package com.socialcoisa.rede.profile.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.socialcoisa.rede.profile.application.port.out.ProfileExtensionRepository;
import com.socialcoisa.rede.profile.application.port.out.RecommendationRepository;
import com.socialcoisa.rede.profile.application.port.out.UserProfileRepository;
import com.socialcoisa.rede.profile.domain.UserProfile;

@ExtendWith(MockitoExtension.class)
class ReputationServiceTests {

	private static final String USER = "user_reputation";

	@Mock
	private UserProfileRepository profileRepository;

	@Mock
	private ProfileExtensionRepository extensionRepository;

	@Mock
	private RecommendationRepository recommendationRepository;

	private ReputationService service;

	@BeforeEach
	void setUp() {
		service = new ReputationService(profileRepository, extensionRepository, recommendationRepository);
	}

	@Test
	void verifiedAccountKeepsTheComputedReputation() {
		UserProfile profile = profile();
		profile.markEmailVerified(Instant.now());
		when(profileRepository.findById(USER)).thenReturn(Optional.of(profile));
		stubActivity();

		ReputationService.Counts counts = service.countsFor(USER);

		assertThat(counts.identityVerified()).isTrue();
		// 3 experiencias x10 + 5 conexoes + 4 seguidores/2 + 2 interacoes = 39
		assertThat(counts.reputation()).isEqualTo(39);
	}

	@Test
	void secondFactorAlsoCountsAsVerification() {
		UserProfile profile = profile();
		profile.enableTotp(Instant.now());
		when(profileRepository.findById(USER)).thenReturn(Optional.of(profile));
		stubActivity();

		ReputationService.Counts counts = service.countsFor(USER);

		assertThat(counts.identityVerified()).isTrue();
		assertThat(counts.reputation()).isPositive();
	}

	@Test
	void reputationIsMultipliedByZeroWithoutEmailVerificationOrSecondFactor() {
		UserProfile profile = profile();
		when(profileRepository.findById(USER)).thenReturn(Optional.of(profile));
		stubActivity();

		ReputationService.Counts counts = service.countsFor(USER);

		assertThat(counts.identityVerified()).isFalse();
		assertThat(counts.reputation()).isZero();
		// As conexoes continuam sendo contadas: o que zera e a credibilidade, nao o perfil.
		assertThat(counts.connections()).isEqualTo(5);
	}

	@Test
	void reportPenaltyStillAppliesOnTopOfTheRule() {
		UserProfile profile = profile();
		profile.markEmailVerified(Instant.now());
		when(profileRepository.findById(USER)).thenReturn(Optional.of(profile));
		stubActivity();
		when(profileRepository.countActiveReportsAgainst(USER)).thenReturn(1);

		// 3 denuncias custam 50 * 3 * 4 / 2 = 300, mais que a atividade deste cenario.
		when(profileRepository.countActiveReportsAgainst(USER)).thenReturn(3);

		assertThat(service.countsFor(USER).reputation()).isZero();
	}

	private void stubActivity() {
		when(profileRepository.countConnections(USER)).thenReturn(5);
		when(profileRepository.countFollowers(USER)).thenReturn(4);
		when(profileRepository.countPostInteractionsReceived(USER)).thenReturn(2);
		when(profileRepository.countCommunitiesJoined(USER)).thenReturn(0);
		when(profileRepository.countEventsAttended(USER)).thenReturn(0);
		when(profileRepository.countMentoringSessionsAsMentee(USER)).thenReturn(0);
		when(profileRepository.countActiveReportsAgainst(USER)).thenReturn(0);
		when(extensionRepository.countExperiencesByUserId(anyString())).thenReturn(3);
		when(extensionRepository.countEducationsByUserId(anyString())).thenReturn(0);
		when(extensionRepository.countCertificationsByUserId(anyString())).thenReturn(0);
		when(recommendationRepository.findByRecommendedId(USER)).thenReturn(List.of());
	}

	private UserProfile profile() {
		return UserProfile.create(USER, "pessoa", USER + "@test.com", "Pessoa", null, Instant.now());
	}
}