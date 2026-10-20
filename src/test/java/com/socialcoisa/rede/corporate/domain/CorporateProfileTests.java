package com.socialcoisa.rede.corporate.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;

/**
 * O alter ego corporativo no domínio. A regra que a dona não deixaria passar: o perfil
 * profissional NUNCA nasce com dados do social — os campos partem vazios e o que for trazido
 * do perfil pessoal chega por fluxo de importação com revisão (na camada de aplicação).
 */
class CorporateProfileTests {

	private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

	@Test
	void nasceEmRascunhoSemNadaCopiadoDoSocial() {
		CorporateProfile profile = CorporateProfile.create("user_1", "mel.santos", "Mel Santos", now);

		assertThat(profile.status()).isEqualTo(CorporateProfileStatus.DRAFT);
		// Nada é copiado do perfil social: headline, bio, avatar e banner partem vazios.
		assertThat(profile.headline()).isNull();
		assertThat(profile.bio()).isNull();
		assertThat(profile.avatar()).isNull();
		assertThat(profile.banner()).isNull();
		// O @ é normalizado e vive em namespace próprio.
		assertThat(profile.username()).isEqualTo("mel.santos");
	}

	@Test
	void aceitaFormatosCoerentesEEsperados() {
		assertThat(CorporateProfile.create("user_1", "Mel.Santos", "Mel Santos", now).username())
			.isEqualTo("mel.santos");
		assertThat(CorporateProfile.create("user_1", "mel-santos", "Mel Santos", now).username())
			.isEqualTo("mel-santos");
		assertThat(CorporateProfile.create("user_1", "mel_santos", "Mel Santos", now).username())
			.isEqualTo("mel_santos");
	}

	@Test
	void recusaUsernameInvalido() {
		assertThatThrownBy(() -> CorporateProfile.create("user_1", "a", "Mel", now))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> CorporateProfile.create("user_1", "-mel", "Mel", now))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> CorporateProfile.create("user_1", "mel com espaco", "Mel", now))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void cicloDeVidaRascunhoAtivoDesativadoEVolta() {
		CorporateProfile profile = CorporateProfile.create("user_1", "mel.santos", "Mel Santos", now);

		CorporateProfile ativo = profile.withDetails("Engenheira de software", "Bio profissional", null, null,
			null, "São Paulo", "Tecnologia", null, now.plus(1, ChronoUnit.MINUTES))
			.activate(now.plus(2, ChronoUnit.MINUTES));
		assertThat(ativo.active()).isTrue();
		assertThat(ativo.headline()).isEqualTo("Engenheira de software");

		// Desativar não apaga: os dados continuam no registro, só o estado muda.
		CorporateProfile desativado = ativo.disable(now.plus(3, ChronoUnit.MINUTES));
		assertThat(desativado.status()).isEqualTo(CorporateProfileStatus.DISABLED);
		assertThat(desativado.headline()).isEqualTo("Engenheira de software");

		// Reabrir volta como ativo (nada foi perdido na desativação).
		assertThat(desativado.reopen(now.plus(4, ChronoUnit.MINUTES)).active()).isTrue();
	}

	@Test
	void renomearAPresentacaoNaoMudaOAt() {
		CorporateProfile profile = CorporateProfile.create("user_1", "mel.santos", "Mel", now);

		CorporateProfile renomeado = profile.withName("Mel de Sousa", now.plus(1, ChronoUnit.MINUTES));

		assertThat(renomeado.name()).isEqualTo("Mel de Sousa");
		assertThat(renomeado.username()).isEqualTo("mel.santos");
	}
}
