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

	/** Mesma busca, mas restrita a UM agregador (a aba externa filtra por plataforma). */
	List<ExternalJob> findByPlatform(String platform, String query, int limit);

	/** Os agregadores disponíveis (para popular o filtro) — distintos e ordenados. */
	List<String> distinctPlatforms();

	/** Resolve as salvas: o id pode ser de uma vaga externa. */
	List<ExternalJob> findByIds(List<String> ids);

	/** As vagas externas cadastradas por ESTA pessoa (tela "Minhas vagas"). */
	List<ExternalJob> findBySubmitter(String userId);

	/** Exclusao definitiva (somente para as cadastradas pela comunidade). */
	void delete(String id);
}
