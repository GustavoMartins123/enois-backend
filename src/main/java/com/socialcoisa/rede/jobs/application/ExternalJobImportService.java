package com.socialcoisa.rede.jobs.application;

import java.time.Clock;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.socialcoisa.rede.jobs.application.port.out.ExternalJobRepository;
import com.socialcoisa.rede.jobs.application.port.out.JobImportSource;
import com.socialcoisa.rede.jobs.domain.ExternalJob;

/**
 * Importa/atualiza vagas de fontes externas (ex.: JobRadar) na tabela PROPRIA `external_jobs` — em
 * vez de buscar a cada requisicao. Dedupe por (plataforma, id externo): reimportar nao duplica. A
 * falha de uma fonte nao derruba as demais nem as vagas internas.
 */
@Service
public class ExternalJobImportService {

	private final List<JobImportSource> sources;
	private final ExternalJobRepository repository;
	private final Clock clock;
	private final int limit;

	public ExternalJobImportService(List<JobImportSource> sources, ExternalJobRepository repository, Clock clock,
			@Value("${rede.jobs.import.external-refresh-limit:50}") int limit) {
		this.sources = sources;
		this.repository = repository;
		this.clock = clock;
		this.limit = limit;
	}

	/** Retorna quantas vagas externas foram importadas/atualizadas nesta passada. */
	@Transactional
	public int refresh() {
		int touched = 0;
		for (JobImportSource source : sources) {
			if (!source.available()) {
				continue;
			}
			List<JobImportSource.ExternalJob> found;
			try {
				// O try/catch cobre SO a chamada externa (fonte fora do ar). Erro de banco NAO e
				// engolido, para nao deixar a transacao em rollback-only.
				found = source.discover(limit);
			}
			catch (RuntimeException failure) {
				continue;
			}
			for (JobImportSource.ExternalJob external : found) {
				if (external.externalId() == null || external.title() == null) {
					continue; // sem id/titulo nao da para deduplicar nem mostrar: ignora
				}
				var existing = repository.findBySource(source.name(), external.externalId());
				String id = existing.map(ExternalJob::id)
					.orElseGet(() -> "ext_" + source.name() + "_" + external.externalId());
				var importedAt = existing.map(ExternalJob::importedAt).orElseGet(clock::instant);
				repository.save(new ExternalJob(id, source.name(), external.externalId(), external.title(),
					external.company(), external.location(), external.salary(), null, external.url(),
					importedAt, clock.instant(), ExternalJob.FOUND));
				touched++;
			}
		}
		return touched;
	}
}
