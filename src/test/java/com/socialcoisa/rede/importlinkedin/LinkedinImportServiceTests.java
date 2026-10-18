package com.socialcoisa.rede.importlinkedin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.socialcoisa.rede.shared.web.ApiException;

/**
 * O parser do ARQUIVO DE DADOS do LinkedIn: CSVs com BOM, campos com aspas/vírgula,
 * datas "YYYY-MM" e "MMM YYYY" (pt/en) e a recusa do que não é exportação do LinkedIn.
 *
 * Nota de Java: as linhas com aspas ESCAPADAS de CSV ("") são construídas por
 * concatenação de strings normais — dentro de text block, qualquer """ encerra o
 * bloco (e \u0022 não salva: escapes unicode viram aspa ANTES do lexer).
 */
class LinkedinImportServiceTests {

	private final LinkedinImportService service = new LinkedinImportService();

	private MockMultipartFile zip(String... csvs) {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			for (int i = 0; i < csvs.length; i += 2) {
				zip.putNextEntry(new ZipEntry(csvs[i]));
				zip.write(csvs[i + 1].getBytes(StandardCharsets.UTF_8));
				zip.closeEntry();
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return new MockMultipartFile("file", "linkedin.zip", "application/zip", bytes.toByteArray());
	}

	private static final String POSITIONS_HEADER = "\"Position Title\",\"Company Name\",\"Location\",\"Description\",\"Started On\",\"Finished On\"";

	@Test
	void parsesPositionsFullRoundTrip() {
		// A linha com aspas ESCAPADAS de CSV ("") vem por concatenação (ver nota da classe).
		String linhaComAspas = "\"Engenheira de Software\",\"Énóis, LTDA\",\"Remoto\","
			+ "\"Construí coisas, com vírgulas, e \"\"aspas\"\" dentro.\""
			+ ",\"2020-01\",\"2022-06\"";
		var preview = service.parse(zip(
			"Positions.csv", POSITIONS_HEADER + "\n" + linhaComAspas + "\n"
				+ "\"Tech Lead\",\"Nubank\",\"São Paulo\",\"Liderança de time\",\"\",\"\"",
			"Education.csv", "",
			"Certifications.csv", "",
			"Projects.csv", ""));

		assertThat(preview.experiences()).hasSize(2);
		var first = preview.experiences().get(0);
		assertThat(first.title()).isEqualTo("Engenheira de Software");
		assertThat(first.company()).isEqualTo("Énóis, LTDA");
		assertThat(first.description()).isEqualTo("Construí coisas, com vírgulas, e \"aspas\" dentro.");
		assertThat(first.startDate()).isEqualTo("2020-01");
		assertThat(first.endDate()).isEqualTo("2022-06");
		assertThat(first.currentlyHere()).isFalse();

		var second = preview.experiences().get(1);
		assertThat(second.company()).isEqualTo("Nubank");
		assertThat(second.startDate()).isNull();
		assertThat(second.currentlyHere()).isTrue();
	}

	@Test
	void parsesEducationCertificationsAndProjects() {
		var preview = service.parse(zip(
			"Positions.csv", POSITIONS_HEADER + "\n\"Dev\",\"Acme\",\"Remoto\",\"\",\"2020-01\",\"\"",
			"Education.csv", "\"School Name\",\"Degree\",\"Field of Study\",\"Notes\",\"Start Date\",\"End Date\"\n"
				+ "\"UFPE\",\"Bacharelado\",\"Ciência da Computação\",\"\",\"2012-03\",\"2017-12\"",
			"Certifications.csv", "\"Name\",\"Authority\",\"URL\",\"Time Period\"\n"
				+ "\"AWS Solutions Architect\",\"Amazon Web Services\",\"https://credly.com/x\",\"2021-05\"",
			"Projects.csv", "\"Title\",\"Description\",\"URL\",\"Started On\",\"Finished On\"\n"
				+ "\"compilador\",\"um compilador de verdade\",\"https://github.com/eu/comp\",\"2023-02\",\"2024-01\""));

		assertThat(preview.education()).hasSize(1);
		assertThat(preview.education().get(0).school()).isEqualTo("UFPE");
		assertThat(preview.education().get(0).startDate()).isEqualTo("2012-03");
		assertThat(preview.certifications()).hasSize(1);
		assertThat(preview.certifications().get(0).name()).isEqualTo("AWS Solutions Architect");
		assertThat(preview.certifications().get(0).issueDate()).isEqualTo("2021-05");
		assertThat(preview.projects()).hasSize(1);
		assertThat(preview.projects().get(0).url()).isEqualTo("https://github.com/eu/comp");
		assertThat(preview.found()).isEqualTo(4);
	}

	@Test
	void parsesPortugueseMonthNames() {
		var preview = service.parse(zip(
			"Positions.csv", "\"Position Title\",\"Company Name\",\"Started On\"\n\"Dev\",\"Acme\",\"mar de 2021\"",
			"Education.csv", "", "Certifications.csv", "", "Projects.csv", ""));

		assertThat(preview.experiences().get(0).startDate()).isEqualTo("2021-03");
	}

	@Test
	void refusesNonLinkedinFile() {
		assertThatThrownBy(() -> service.parse(zip(
			"outra-coisa.csv", "nada aqui")))
			.isInstanceOf(ApiException.class)
			.hasMessageContaining("Não encontrei os CSVs");
	}

	@Test
	void refusesEmptyAndHuge() {
		assertThatThrownBy(() -> service.parse(null)).isInstanceOf(ApiException.class);
		assertThatThrownBy(() -> service.parse(new MockMultipartFile("file", "x.zip", "application/zip",
			new byte[5 * 1024 * 1024 + 1]))).isInstanceOf(ApiException.class);
	}
}
