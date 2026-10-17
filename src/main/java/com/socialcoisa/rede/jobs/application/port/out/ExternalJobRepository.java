package com.socialcoisa.rede.jobs.application.port.out;

import java.util.List;
import java.util.Optional;

import com.socialcoisa.rede.jobs.domain.ExternalJob;

/** Persistencia das vagas externas (tabela propria, separada das vagas da plataforma). */
public interface ExternalJobRepository {

	Optional<ExternalJob> findById(String id);

	Optional<ExternalJob> findBySource(String platform, String externalId);

	/** Deduplicacao GLOBAL: mesma vaga (mesma URL canonica) achada por outra fonte. */
	Optional<ExternalJob> findByCanonicalUrl(String canonicalUrl);

	void save(ExternalJob job);

	/** Vagas externas mais recentes primeiro (o que sera listado depois das internas). */
	List<ExternalJob> findRecent(int limit);

	/** Busca por TITULO (as externas nao tem tipo/nivel/local/salario para filtrar). */
	List<ExternalJob> search(String query, int limit);
}
