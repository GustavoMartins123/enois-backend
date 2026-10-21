package com.socialcoisa.rede.importresume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.socialcoisa.rede.importlinkedin.LinkedinImportService;
import com.socialcoisa.rede.shared.web.ApiException;

/**
 * O import via curriculo: arquivo vira texto, texto vira retrato (mesmo formato do LinkedIn)
 * e o prefill cruza os campos da vaga com os contatos do curriculo — sem salvar nada.
 */
class ResumeImportServiceTests {

	private final ResumeImportService service = new ResumeImportService(new LinkedinImportService());

	private MockMultipartFile txt(String name, String content) {
		return new MockMultipartFile("file", name, "text/plain", content.getBytes(StandardCharsets.UTF_8));
	}

	@Test
	void textoComSecoesViraRetratoNoFormatoDoLinkedin() {
		String resume = """
			Ana Souza
			Engenheira de Software

			Sobre
			Engenheira com 5 anos de experiência em Java e Spring.

			Experiencia
			Engenheira de Software
			Enois
			jan de 2021 - o momento
			São Paulo, Brasil
			APIs em Java com Spring Boot.

			Educacao
			Ciencia da Computacao
			USP
			2015 - 2019

			Habilidades
			Java, Spring, PostgreSQL
			""";
		var preview = service.parse(txt("curriculo.txt", resume));
		assertThat(preview.profile().name()).isEqualTo("Ana Souza");
		assertThat(preview.experiences()).hasSize(1);
		assertThat(preview.experiences().get(0).company()).isEqualTo("Enois");
		assertThat(preview.experiences().get(0).startDate()).isEqualTo("2021-01-01");
		assertThat(preview.skills()).contains("Java", "Spring");
		assertThat(preview.found()).isPositive();
	}

	@Test
	void recusaDocxOrientandoExportarEmPdf() {
		var docx = new MockMultipartFile("file", "curriculo.docx",
			"application/vnd.openxmlformats-officedocument.wordprocessingml.document", "PK fake".getBytes());
		assertThatThrownBy(() -> service.parse(docx))
			.isInstanceOf(ApiException.class)
			.hasMessageContaining("PDF");
	}

	@Test
	void recusaTextoCurtoDemais() {
		assertThatThrownBy(() -> service.parse(txt("curriculo.txt", "Ana")))
			.isInstanceOf(ApiException.class)
			.hasMessageContaining("texto legivel");
	}

	@Test
	void prefillSugereEmailTelefoneELinkNosCamposCompativeis() {
		String resume = "Ana Souza\nana.souza@mail.com\n(11) 98765-4321\n"
			+ "https://www.linkedin.com/in/anasouza\n5 anos de experiência em Java.";
		var fields = List.of(
			new ResumeImportService.JobField("f1", "LinkedIn", ResumeImportService.JobFieldType.LINK, false),
			new ResumeImportService.JobField("f2", "Telefone", ResumeImportService.JobFieldType.LINK, false),
			new ResumeImportService.JobField("f3", "Anos de experiência",
				ResumeImportService.JobFieldType.NUMBER, true));
		var prefill = service.prefill("job-1", "Dev Java", fields, resume);
		assertThat(prefill.jobId()).isEqualTo("job-1");
		assertThat(prefill.fields()).hasSize(3);
		assertThat(prefill.fields().get(0).suggestedValue()).contains("linkedin.com");
		assertThat(prefill.fields().get(1).suggestedValue()).contains("98765");
		assertThat(prefill.fields().get(2).suggestedValue()).isEqualTo("5");
		assertThat(prefill.suggestedMessage()).contains("ana.souza@mail.com");
		assertThat(prefill.filled()).isEqualTo(3);
	}

	@Test
	void prefillMarcaMotivoQuandoNaoAchaNada() {
		var fields = List.of(
			new ResumeImportService.JobField("f1", "LinkedIn", ResumeImportService.JobFieldType.LINK, false),
			new ResumeImportService.JobField("f2", "Carta de apresentacao",
				ResumeImportService.JobFieldType.TEXTAREA, false));
		var prefill = service.prefill("job-1", "Dev Java", fields, "nome e texto corrido sem contatos aqui mesmo");
		assertThat(prefill.fields().get(0).reason()).isEqualTo("NO_LINK_FOUND");
		assertThat(prefill.fields().get(1).reason()).isEqualTo("MANUAL_FIELD");
		assertThat(prefill.filled()).isZero();
	}
}
