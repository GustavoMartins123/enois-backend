package com.socialcoisa.rede.jobs.adapters.out.persistence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Repository;

import com.socialcoisa.rede.jobs.application.port.out.CompanyDirectoryReader;

/**
 * Diretorio de empresas derivado das vagas: internas (abertas) + externas agregadas por nome,
 * sem diferenciar maiusculas/minusculas. Sem entidade Company ainda — quando a Fase 1 do
 * COMPANY_PAGES existir, o diretorio passa a ler de companies.
 */
@Repository
class CompanyDirectoryReaderAdapter implements CompanyDirectoryReader {

	private final SpringDataJobJpaRepository jobs;
	private final SpringDataExternalJobJpaRepository externalJobs;

	CompanyDirectoryReaderAdapter(SpringDataJobJpaRepository jobs, SpringDataExternalJobJpaRepository externalJobs) {
		this.jobs = jobs;
		this.externalJobs = externalJobs;
	}

	@Override
	public List<CompanySummary> directory(String query, int limit) {
		Map<String, long[]> totals = new HashMap<>();
		Map<String, String> display = new LinkedHashMap<>();
		Map<String, String> logos = new HashMap<>();

		merge(jobs.countOpenJobsByCompany(), totals, display, logos);
		merge(externalJobs.countJobsByCompany(), totals, display, logos);

		String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
		List<CompanySummary> all = new ArrayList<>();
		for (var key : display.keySet()) {
			if (!needle.isEmpty() && !key.contains(needle) && !display.get(key).toLowerCase(Locale.ROOT).contains(needle)) {
				continue;
			}
			long[] agg = totals.get(key);
			all.add(new CompanySummary(display.get(key), logos.get(key), agg[0]));
		}
		all.sort((a, b) -> Long.compare(b.openJobs(), a.openJobs()));
		return all.subList(0, Math.min(limit, all.size()));
	}

	private void merge(List<Object[]> rows, Map<String, long[]> totals, Map<String, String> display,
			Map<String, String> logos) {
		for (Object[] row : rows) {
			String name = String.valueOf(row[0]);
			String key = name.toLowerCase(Locale.ROOT);
			long count = ((Number) row[1]).longValue();
			totals.computeIfAbsent(key, k -> new long[1])[0] += count;
			display.putIfAbsent(key, name);
			if (row.length > 2 && row[2] != null) {
				logos.putIfAbsent(key, String.valueOf(row[2]));
			}
		}
	}
}
