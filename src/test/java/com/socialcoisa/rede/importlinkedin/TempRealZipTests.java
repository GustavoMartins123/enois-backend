package com.socialcoisa.rede.importlinkedin;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/** TEMPORARIO (diagnostico): roda o parser contra o ZIP real que a usuaria enviou. */
class TempRealZipTests {

	@Test
	void parseTheRealExport() throws Exception {
		Path zip = Path.of("/home/mel/Downloads/Basic_LinkedInDataExport_10-20-2026.zip.zip");
		if (!Files.exists(zip)) {
			System.out.println("SEM ARQUIVO");
			return;
		}
		var service = new LinkedinImportService();
		var preview = service.parse(new MockMultipartFile("file", "linkedin.zip",
			"application/zip", Files.readAllBytes(zip)));
		System.out.println("EXPERIENCIAS=" + preview.experiences().size());
		System.out.println("EDUCACAO=" + preview.education().size());
		System.out.println("CERTIFICACOES=" + preview.certifications().size());
		System.out.println("PROJETOS=" + preview.projects().size());
		System.out.println("SKILLS=" + preview.skills().size());
		System.out.println("PROFILE=" + preview.profile());
		System.out.println("SEM_ESTRUTURA=" + preview.semEstrutura());
		System.out.println("FOUND=" + preview.found());
	}
}
