package com.socialcoisa.rede.jobs.application.port.out;

import java.util.List;

/** Leitura do diretorio de empresas derivado das vagas (internas + externas). */
public interface CompanyDirectoryReader {

	record CompanySummary(String name, String logo, long openJobs) {
	}

	List<CompanySummary> directory(String query, int limit);
}
