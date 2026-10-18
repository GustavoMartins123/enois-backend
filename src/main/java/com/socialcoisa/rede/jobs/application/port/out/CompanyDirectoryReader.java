package com.socialcoisa.rede.jobs.application.port.out;

import java.util.List;

/** Leitura do diretorio de empresas derivado das vagas (internas + externas). */
public interface CompanyDirectoryReader {

	record CompanySummary(String name, String logo, long openJobs, String origem) {
	}

	/** As duas origens do diretório (a aba do front separa Énóis × plataformas externas). */
	String ORIGEM_ENOIS = "enois";
	String ORIGEM_EXTERNA = "externa";

	List<CompanySummary> directory(String query, int limit);
}
