package com.socialcoisa.rede.games.adapters.out.persistence;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataGameCommentJpaRepository extends JpaRepository<GameCommentJpaEntity, String> {

	List<GameCommentJpaEntity> findByGameIdOrderByCreatedAtDesc(String gameId, Pageable pageable);

	long countByGameId(String gameId);
}
