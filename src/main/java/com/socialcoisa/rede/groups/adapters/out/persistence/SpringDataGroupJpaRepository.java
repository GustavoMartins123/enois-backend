package com.socialcoisa.rede.groups.adapters.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.socialcoisa.rede.groups.domain.GroupStatus;

interface SpringDataGroupJpaRepository extends JpaRepository<GroupJpaEntity, String> {

	Optional<GroupJpaEntity> findBySlug(String slug);

	boolean existsBySlug(String slug);

	/**
	 * Descoberta: só os grupos visíveis, com busca opcional por nome ou descrição.
	 *
	 * A busca trabalha APENAS com metadados públicos — publicações, membros e tópicos internos não
	 * entram aqui, nem por acidente de junção.
	 */
	@Query("""
		SELECT g FROM GroupJpaEntity g
		WHERE g.status IN :statuses
		  AND (:before IS NULL OR g.createdAt < :before)
		  AND (:query IS NULL OR LOWER(g.name) LIKE :query OR LOWER(g.description) LIKE :query)
		ORDER BY g.createdAt DESC""")
	List<GroupJpaEntity> listVisible(@Param("statuses") List<GroupStatus> statuses, @Param("query") String query,
			@Param("before") Instant before, Pageable pageable);
}
