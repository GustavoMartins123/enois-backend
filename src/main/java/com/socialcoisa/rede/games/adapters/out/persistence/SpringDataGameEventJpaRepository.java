package com.socialcoisa.rede.games.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.socialcoisa.rede.games.domain.GameEventKind;

interface SpringDataGameEventJpaRepository extends JpaRepository<GameEventJpaEntity, String> {

	long countByGameIdAndKind(String gameId, GameEventKind kind);

	/** Jogadores distintos estimados: quem tem sessão conta uma vez, anônimo conta por evento. */
	@Query("SELECT COUNT(DISTINCT COALESCE(e.userId, e.id)) FROM GameEventJpaEntity e "
		+ "WHERE e.gameId = :gameId AND e.kind = :kind")
	long distinctPlayers(@Param("gameId") String gameId, @Param("kind") GameEventKind kind);
}
