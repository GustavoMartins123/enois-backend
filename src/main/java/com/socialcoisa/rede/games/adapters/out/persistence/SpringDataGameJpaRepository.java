package com.socialcoisa.rede.games.adapters.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.socialcoisa.rede.games.domain.GameStatus;

interface SpringDataGameJpaRepository extends JpaRepository<GameJpaEntity, String> {

	Optional<GameJpaEntity> findByUserIdAndSlug(String userId, String slug);

	boolean existsByUserIdAndSlug(String userId, String slug);

	List<GameJpaEntity> findByUserIdOrderByUpdatedAtDesc(String userId);

	List<GameJpaEntity> findByUserIdAndStatusOrderBySubmittedAtDesc(String userId, GameStatus status);

	List<GameJpaEntity> findByStatusOrderBySubmittedAtDesc(GameStatus status, Pageable pageable);

	List<GameJpaEntity> findByStatusOrderBySubmittedAtDesc(GameStatus status);
}
