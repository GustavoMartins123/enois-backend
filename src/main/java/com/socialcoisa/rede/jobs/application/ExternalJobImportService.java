package com.socialcoisa.rede.jobs.application;

import java.time.Clock;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.socialcoisa.rede.jobs.application.port.out.ExternalJobRepository;
import com.socialcoisa.rede.jobs.application.port.out.JobImportSource;
import com.socialcoisa.rede.jobs.domain.ExternalJob;

/**
 * Importa/atualiza vagas de fontes externas (ex.: JobRadar) na tabela PROPRIA `external_jobs`.
 *
 * Carrega TUDO que a fonte publica (nao uma amostra). Para nao estourar memoria nem uma unica
 * transacao gigante, grava em LOTES, cada lote com sua transacao. Dedupe por (plataforma, id externo):
 * reimportar nao duplica. Falha de uma fonte nao derruba as demais nem as vagas internas.
 */
@Service
public class ExternalJobImportService {

	/** Tamanho do lote: transacao curta por bloco, em vez de uma transacao de milhares de linhas. */
	private static final int BATCH_SIZE = 200;

	private final List<JobImportSource> sources;
	private final ExternalJobRepository repository;
	private final Clock clock;
	private final int limit;
	private final TransactionTemplate transactionTemplate;

	public ExternalJobImportService(List<JobImportSource> sources, ExternalJobRepository repository, Clock clock,
			PlatformTransactionManager transactionManager,
			@Value("${rede.jobs.import.external-refresh-limit:0}") int limit) {
		this.sources = sources;
		this.repository = repository;
		this.clock = clock;
		this.limit = limit;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	/** Retorna quantas vagas externas foram importadas/atualizadas nesta passada. */
	public int refresh() {
		int touched = 0;
		for (JobImportSource source : sources) {
			if (!source.available()) {
				continue;
			}
			List<JobImportSource.ExternalJob> found;
			try {
				// O try/catch cobre SO a chamada externa (fonte fora do ar). Erro de banco NAO e
				// engolido, para nao deixar transacao em rollback-only.
				found = source.discover(limit);
			}
			catch (RuntimeException failure) {
				continue;
			}
			for (int i = 0; i < found.size(); i += BATCH_SIZE) {
				List<JobImportSource.ExternalJob> chunk = found.subList(i, Math.min(i + BATCH_SIZE, found.size()));
				Integer saved = transactionTemplate.execute(status -> saveChunk(source.name(), chunk));
				touched += saved == null ? 0 : saved;
			}
		}
		return touched;
	}

	private int saveChunk(String platform, List<JobImportSource.ExternalJob> chunk) {
		int saved = 0;
		for (JobImportSource.ExternalJob external : chunk) {
			if (external.externalId() == null || external.title() == null) {
				continue; // sem id/titulo nao da para deduplicar nem mostrar: ignora
			}
			var existing = repository.findBySource(platform, external.externalId());
			String id = existing.map(ExternalJob::id).orElseGet(() -> "ext_" + platform + "_" + external.externalId());
			var importedAt = existing.map(ExternalJob::importedAt).orElseGet(clock::instant);
			repository.save(new ExternalJob(id, platform, external.externalId(), external.title(),
				external.company(), external.location(), external.salary(), null, external.url(), importedAt,
				clock.instant(), ExternalJob.FOUND));
			saved++;
		}
		return saved;
	}
}
