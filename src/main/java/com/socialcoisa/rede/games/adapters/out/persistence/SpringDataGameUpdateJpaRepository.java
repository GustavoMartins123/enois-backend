package com.socialcoisa.rede.games.adapters.out.persistence;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataGameUpdateJpaRepository extends JpaRepository<GameUpdateJpaEntity, String> {

	List<GameUpdateJpaEntity> findByGameIdOrderByCreatedAtDesc(String gameId, Pageable pageable);

	long countByGameId(String gameId);

	@org.springframework.data.jpa.repository.Modifying
	@org.springframework.data.jpa.repository.Query("DELETE FROM GameUpdateJpaEntity u WHERE u.gameId = :gameId")
	int deleteByGameId(@org.springframework.data.repository.query.Param("gameId") String gameId);
}
