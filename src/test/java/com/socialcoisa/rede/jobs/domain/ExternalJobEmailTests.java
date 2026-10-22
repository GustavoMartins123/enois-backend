package com.socialcoisa.rede.jobs.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * E-mail de candidatura extraido do anuncio externo.
 *
 * E o que faz o botao "agilizar envio" aparecer: sem endereco, nao ha o que pre-escrever. A regra
 * tem duas preocupacoes — nao deixar passar endereco que nao e caixa de candidatura (disparo
 * automatico, imagem) e sobreviver ao HTML, que e como a descricao chega.
 */
class ExternalJobEmailTests {

	@Test
	void findsTheEmailInsideHtml() {
		String description = "<p>Mande seu curriculo para <strong>jobs@empresa.com</strong> com o assunto "
			+ "\"Vaga Back-end\".</p>";

		assertThat(ExternalJob.extractApplyEmail(description)).isEqualTo("jobs@empresa.com");
	}

	@Test
	void skipsNoreplyAndAssets() {
		String description = "contato: noreply@empresa.com e banner@empresa.com/logo.png "
			+ "| candidaturas: vagas@empresa.com";

		assertThat(ExternalJob.extractApplyEmail(description)).isEqualTo("vagas@empresa.com");
	}

	@Test
	void normalizesCaseAndHandlesMissingText() {
		assertThat(ExternalJob.extractApplyEmail("Envie para Vagas@Empresa.COM")).isEqualTo("vagas@empresa.com");
		assertThat(ExternalJob.extractApplyEmail(null)).isNull();
		assertThat(ExternalJob.extractApplyEmail("sem endereco nenhum aqui")).isNull();
	}
}
