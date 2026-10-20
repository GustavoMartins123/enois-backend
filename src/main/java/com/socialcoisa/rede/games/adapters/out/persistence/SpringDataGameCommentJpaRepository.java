package com.socialcoisa.rede.games.adapters.out.persistence;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataGameCommentJpaRepository extends JpaRepository<GameCommentJpaEntity, String> {

	List<GameCommentJpaEntity> findByGameIdOrderByCreatedAtDesc(String gameId, Pageable pageable);

	long countByGameId(String gameId);

	@Query("SELECT c.gameId, COUNT(c) FROM GameCommentJpaEntity c WHERE c.gameId IN :gameIds GROUP BY c.gameId")
	List<Object[]> countByGameIds(@Param("gameIds") java.util.Collection<String> gameIds);
}
