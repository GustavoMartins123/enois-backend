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

	List<ExternalJobJpaEntity> findByTitleContainingIgnoreCaseOrderByLastCheckedAtDesc(String query, Pageable pageable);
}
