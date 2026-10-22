package com.socialcoisa.rede.games.adapters.out.persistence;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import com.socialcoisa.rede.games.application.port.out.GameUpdateRepository;
import com.socialcoisa.rede.games.domain.GameUpdate;

@Repository
class GameUpdatePersistenceAdapter implements GameUpdateRepository {

	private final SpringDataGameUpdateJpaRepository updates;

	GameUpdatePersistenceAdapter(SpringDataGameUpdateJpaRepository updates) {
		this.updates = updates;
	}

	@Override
	public GameUpdate save(GameUpdate update) {
		return updates.save(GameUpdateJpaEntity.fromDomain(update)).toDomain();
	}

	@Override
	public List<GameUpdate> findByGame(String gameId, int limit) {
		return updates.findByGameIdOrderByCreatedAtDesc(gameId, PageRequest.of(0, Math.max(1, limit))).stream()
			.map(GameUpdateJpaEntity::toDomain).toList();
	}

	@Override
	public long countByGame(String gameId) {
		return updates.countByGameId(gameId);
	}

	@Override
	public void deleteByGame(String gameId) {
		updates.deleteByGameId(gameId);
	}
}
