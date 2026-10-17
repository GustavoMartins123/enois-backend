package com.socialcoisa.rede.jobs.adapters.out.imports;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.socialcoisa.rede.jobs.application.port.out.JobImportSource;

import tools.jackson.databind.ObjectMapper;

/**
 * Fontes de vagas por API PUBLICA (sem chave) — agregadores que permitem listar e redirecionar para a
 * vaga original. Mapeamento declarado por fonte; nada de scraping.
 *
 * Atribuicao: Remotive e Jobicy exigem credito com link de volta. O credito e feito pelo nome da
 * plataforma (selo "Vaga externa · {plataforma}") + a URL da propria fonte na vaga.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "rede.jobs.import.open-apis.enabled", matchIfMissing = true)
class OpenJobApisConfig {

	/** Remotive — vagas remotas; credito obrigatorio (ver docs/JOB_SOURCES.md). */
	@Bean
	JobImportSource remotiveJobSource(ObjectMapper objectMapper) {
		return new OpenJobApiSource(new OpenJobApiSource.Spec("Remotive",
			"https://remotive.com/api/remote-jobs?limit=200", false, "jobs", "id", "title", "company_name",
			"candidate_required_location", "salary", null, null, null, "job_type", "url", null,
			"publication_date", false, null,
			"Remotive (https://remotive.com) — credito obrigatorio."), objectMapper);
	}

	/** Jobicy — API v2 publica; credito + link de origem exigidos. */
	@Bean
	JobImportSource jobicyJobSource(ObjectMapper objectMapper) {
		return new OpenJobApiSource(new OpenJobApiSource.Spec("Jobicy",
			"https://jobicy.com/api/v2/remote-jobs?count=50", false, "jobs", "id", "jobTitle", "companyName",
			"jobGeo", null, "annualSalaryMin", "annualSalaryMax", "salaryCurrency", "jobType[0]", "url", null,
			"pubDate", false, null,
			"Jobicy (https://jobicy.com) — credito e link de origem obrigatorios."), objectMapper);
	}

	/** RemoteOK — JSON publico (primeiro item da lista e um aviso legal; ignorado). */
	@Bean
	JobImportSource remoteOkJobSource(ObjectMapper objectMapper) {
		return new OpenJobApiSource(new OpenJobApiSource.Spec("RemoteOK",
			"https://remoteok.com/api", true, null, "id", "position", "company", "location", null,
			"salary_min", "salary_max", null, null, "url", "apply_url", "date", false, null,
			"RemoteOK (https://remoteok.com) — credito obrigatorio."), objectMapper);
	}

	/** Himalayas — API publica de vagas remotas. */
	@Bean
	JobImportSource himalayasJobSource(ObjectMapper objectMapper) {
		return new OpenJobApiSource(new OpenJobApiSource.Spec("Himalayas",
			"https://himalayas.app/jobs/api?limit=100", false, "jobs", "applicationLink", "title", "companyName",
			"locationRestrictions[0]", null, "minSalary", "maxSalary", "salaryCurrency", null,
			"applicationLink", "applicationLink", "pubDate", true, null,
			"Himalayas (https://himalayas.app)."), objectMapper);
	}
}
