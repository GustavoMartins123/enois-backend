package com.socialcoisa.rede.games.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.socialcoisa.rede.games.domain.GameEventKind;

interface SpringDataGameEventJpaRepository extends JpaRepository<GameEventJpaEntity, String> {

	long countByGameIdAndKind(String gameId, GameEventKind kind);

	long countByGameIdAndKindAndUserIdAndCreatedAtAfter(String gameId, GameEventKind kind, String userId,
			java.time.Instant after);

	/** Jogadores distintos estimados: quem tem sessão conta uma vez, anônimo conta por evento. */
	@Query("SELECT COUNT(DISTINCT COALESCE(e.userId, e.id)) FROM GameEventJpaEntity e "
		+ "WHERE e.gameId = :gameId AND e.kind = :kind")
	long distinctPlayers(@Param("gameId") String gameId, @Param("kind") GameEventKind kind);

	/** Série do painel: eventos desde um instante, mais antigos primeiro (a agregação é em memória). */
	java.util.List<GameEventJpaEntity> findByGameIdAndCreatedAtAfterOrderByCreatedAtAsc(String gameId,
			java.time.Instant from, org.springframework.data.domain.Pageable pageable);

	/**
	 * Ranking da janela: sessões e jogadores distintos agrupados por jogo, mais jogado primeiro. O
	 * `Pageable` é o teto de linhas — o card do feed pede seis, e o resto é conta alheia.
	 */
	@Query("SELECT new com.socialcoisa.rede.games.application.GameTally(e.gameId, COUNT(e.id), "
		+ "COUNT(DISTINCT COALESCE(e.userId, e.id))) FROM GameEventJpaEntity e "
		+ "WHERE e.kind = :kind AND e.createdAt >= :since "
		+ "GROUP BY e.gameId ORDER BY COUNT(e.id) DESC")
	java.util.List<com.socialcoisa.rede.games.application.GameTally> mostPlayedSince(@Param("kind") GameEventKind kind,
			@Param("since") java.time.Instant since, org.springframework.data.domain.Pageable pageable);

	@org.springframework.data.jpa.repository.Modifying
	@Query("DELETE FROM GameEventJpaEntity e WHERE e.gameId = :gameId")
	int deleteByGameId(@Param("gameId") String gameId);
}
