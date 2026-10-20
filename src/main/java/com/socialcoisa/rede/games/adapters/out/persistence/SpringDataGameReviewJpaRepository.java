package com.socialcoisa.rede.games.adapters.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataGameReviewJpaRepository extends JpaRepository<GameReviewJpaEntity, String> {

	Optional<GameReviewJpaEntity> findByGameIdAndUserId(String gameId, String userId);

	List<GameReviewJpaEntity> findByGameIdOrderByCreatedAtDesc(String gameId, Pageable pageable);

	long countByGameId(String gameId);

	@Query("SELECT AVG(r.rating) FROM GameReviewJpaEntity r WHERE r.gameId = :gameId")
	Double averageByGameId(@Param("gameId") String gameId);
}
