package com.socialcoisa.rede.jobs.adapters.out.persistence;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataJobJpaRepository extends JpaRepository<JobJpaEntity, String> {

	// A listagem com filtros (inclusive a busca booleana) e montada com Criteria em
	// JobPersistenceAdapter: texto, nivel, tipo, local e habilidades precisam ser combinados na mesma
	// consulta, o que nao cabia no encadeamento de queries fixas que existia aqui (cada `else if`
	// descartava os outros filtros).

	@Query("SELECT j FROM JobJpaEntity j WHERE j.postedBy = :postedBy AND (:before IS NULL OR j.postedAt < :before) ORDER BY j.postedAt DESC")
	List<JobJpaEntity> findByPostedByAndPostedAtBeforeOrderByPostedAtDesc(@Param("postedBy") String postedBy,
			@Param("before") Instant before, Pageable pageable);

	@Query("SELECT j FROM JobJpaEntity j WHERE j.id IN :ids")
	List<JobJpaEntity> findByIdIn(@Param("ids") java.util.Collection<String> ids);

}