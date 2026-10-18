package com.socialcoisa.rede.jobs.adapters.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface SpringDataExternalJobJpaRepository extends JpaRepository<ExternalJobJpaEntity, String> {

	Optional<ExternalJobJpaEntity> findByPlatformAndExternalId(String platform, String externalId);

	Optional<ExternalJobJpaEntity> findFirstByCanonicalUrl(String canonicalUrl);

	List<ExternalJobJpaEntity> findAllByOrderByLastCheckedAtDesc(Pageable pageable);

	// Mesmo agregado para as vagas externas (Remotive/Jobicy/...): empresa e total (a origem
	// nao guarda logo — o diretorio mostra a inicial quando nao ha logo de vaga interna).
	@Query("SELECT e.company, COUNT(e) AS total FROM ExternalJobJpaEntity e " +
		"WHERE e.company IS NOT NULL GROUP BY e.company ORDER BY COUNT(e) DESC")
	List<Object[]> countJobsByCompany();

	/**
	 * Busca das externas: titulo, EMPRESA ou local — a mesma regua da vaga interna.
	 *
	 * Antes so o titulo casava, e o diretorio de empresas (que e feito de empresas das vagas
	 * externas) levava a uma lista vazia: clicar em "Monzo — 8 vagas" buscava "Monzo", que e a
	 * empresa, e nao achava nada. `escape` e `lower` para o curinga do usuario nao virar busca
	 * ampla e a comparacao nao depender do collation.
	 */
	@Query("""
			SELECT e FROM ExternalJobJpaEntity e
			WHERE LOWER(e.title) LIKE :pattern ESCAPE '\\'
			   OR LOWER(e.company) LIKE :pattern ESCAPE '\\'
			   OR LOWER(e.location) LIKE :pattern ESCAPE '\\'
			ORDER BY e.lastCheckedAt DESC""")
	List<ExternalJobJpaEntity> search(@org.springframework.data.repository.query.Param("pattern") String pattern,
			Pageable pageable);
}
