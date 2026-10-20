package com.socialcoisa.rede.games.adapters.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import com.socialcoisa.rede.games.application.port.out.GameCommentRepository;
import com.socialcoisa.rede.games.domain.GameComment;

@Repository
class GameCommentPersistenceAdapter implements GameCommentRepository {

	private final SpringDataGameCommentJpaRepository repository;

	GameCommentPersistenceAdapter(SpringDataGameCommentJpaRepository repository) {
		this.repository = repository;
	}

	@Override
	public GameComment save(GameComment comment) {
		return repository.save(GameCommentJpaEntity.fromDomain(comment)).toDomain();
	}

	@Override
	public Optional<GameComment> findById(String id) {
		return repository.findById(id).map(GameCommentJpaEntity::toDomain);
	}

	@Override
	public List<GameComment> findByGame(String gameId, int limit) {
		return repository.findByGameIdOrderByCreatedAtDesc(gameId, PageRequest.of(0, Math.max(1, limit)))
			.stream().map(GameCommentJpaEntity::toDomain).toList();
	}

	@Override
	public long countByGame(String gameId) {
		return repository.countByGameId(gameId);
	}

	@Override
	public void delete(String id) {
		repository.deleteById(id);
	}
}
