package com.socialcoisa.rede.games.adapters.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import com.socialcoisa.rede.games.application.port.out.GameRepository;
import com.socialcoisa.rede.games.domain.Game;
import com.socialcoisa.rede.games.domain.GameStatus;

@Repository
class GamePersistenceAdapter implements GameRepository {

	private final SpringDataGameJpaRepository games;

	GamePersistenceAdapter(SpringDataGameJpaRepository games) {
		this.games = games;
	}

	@Override
	public Game save(Game game) {
		return games.save(GameJpaEntity.fromDomain(game)).toDomain();
	}

	@Override
	public Optional<Game> findById(String id) {
		return games.findById(id).map(GameJpaEntity::toDomain);
	}

	@Override
	public Optional<Game> findByUserAndSlug(String userId, String slug) {
		return games.findByUserIdAndSlug(userId, slug).map(GameJpaEntity::toDomain);
	}

	@Override
	public List<Game> findByUser(String userId) {
		return games.findByUserIdOrderByUpdatedAtDesc(userId).stream().map(GameJpaEntity::toDomain).toList();
	}

	@Override
	public List<Game> findByUserAndStatus(String userId, GameStatus status, int limit) {
		return games.findByUserIdAndStatusOrderBySubmittedAtDesc(userId, status).stream()
			.limit(Math.max(1, limit)).map(GameJpaEntity::toDomain).toList();
	}

	@Override
	public List<Game> findByStatus(GameStatus status, int limit) {
		return games.findByStatusOrderBySubmittedAtDesc(status, PageRequest.of(0, Math.max(1, limit))).stream()
			.map(GameJpaEntity::toDomain).toList();
	}

	@Override
	public List<Game> findPublished(int limit) {
		return findByStatus(GameStatus.PUBLISHED, limit);
	}

	@Override
	public boolean existsByUserAndSlug(String userId, String slug) {
		return games.existsByUserIdAndSlug(userId, slug);
	}
}
