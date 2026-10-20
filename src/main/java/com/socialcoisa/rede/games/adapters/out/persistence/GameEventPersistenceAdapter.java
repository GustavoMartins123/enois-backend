package com.socialcoisa.rede.games.adapters.out.persistence;

import java.time.Instant;

import org.springframework.stereotype.Repository;

import com.socialcoisa.rede.games.application.port.out.GameEventRepository;
import com.socialcoisa.rede.games.domain.GameEventKind;

@Repository
class GameEventPersistenceAdapter implements GameEventRepository {

	private final SpringDataGameEventJpaRepository events;

	GameEventPersistenceAdapter(SpringDataGameEventJpaRepository events) {
		this.events = events;
	}

	@Override
	public void record(String id, String gameId, GameEventKind kind, String userId, Instant now) {
		events.save(GameEventJpaEntity.of(id, gameId, kind, userId, now));
	}

	@Override
	public long countByGameAndKind(String gameId, GameEventKind kind) {
		return events.countByGameIdAndKind(gameId, kind);
	}

	@Override
	public long countByGameAndUserAndKindSince(String gameId, GameEventKind kind, String userId, java.time.Instant since) {
		return events.countByGameIdAndKindAndUserIdAndCreatedAtAfter(gameId, kind, userId, since);
	}

	@Override
	public long distinctPlayers(String gameId) {
		return events.distinctPlayers(gameId, GameEventKind.PLAY_START);
	}

	@Override
	public java.util.List<com.socialcoisa.rede.games.domain.GameEvent> findSince(String gameId, Instant from,
			int limit) {
		return events.findByGameIdAndCreatedAtAfterOrderByCreatedAtAsc(gameId, from,
			org.springframework.data.domain.PageRequest.of(0, Math.max(1, limit))).stream()
			.map(GameEventJpaEntity::toDomain).toList();
	}

	@Override
	public void deleteByGame(String gameId) {
		events.deleteByGameId(gameId);
	}
}
