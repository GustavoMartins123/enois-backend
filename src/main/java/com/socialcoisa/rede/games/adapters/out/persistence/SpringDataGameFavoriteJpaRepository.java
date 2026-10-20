package com.socialcoisa.rede.games.adapters.out.persistence;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataGameFavoriteJpaRepository extends JpaRepository<GameFavoriteJpaEntity, String> {

	boolean existsByUserIdAndGameId(String userId, String gameId);

	@Modifying
	@Query("DELETE FROM GameFavoriteJpaEntity f WHERE f.userId = :userId AND f.gameId = :gameId")
	int deleteByUserAndGame(@Param("userId") String userId, @Param("gameId") String gameId);

	long countByGameId(String gameId);

	List<GameFavoriteJpaEntity> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

	@Modifying
	@Query("DELETE FROM GameFavoriteJpaEntity f WHERE f.gameId = :gameId")
	int deleteByGameId(@Param("gameId") String gameId);
}
