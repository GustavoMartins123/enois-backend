package com.socialcoisa.rede.games.adapters.out.persistence;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.socialcoisa.rede.games.application.GameIdentityService;
import com.socialcoisa.rede.games.application.GameIdentityService.GameToken;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "game_tokens")
class GameTokenJpaEntity {

	@Id
	private String id;

	@Column(name = "game_id", nullable = false)
	private String gameId;

	@Column(name = "user_id", nullable = false)
	private String userId;

	@Column(name = "token_hash", nullable = false, length = 64)
	private String tokenHash;

	@Column(name = "player_ref", nullable = false, length = 64)
	private String playerRef;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "used_at")
	private Instant usedAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	static GameTokenJpaEntity from(GameToken t) {
		GameTokenJpaEntity e = new GameTokenJpaEntity();
		e.id = t.id();
		e.gameId = t.gameId();
		e.userId = t.userId();
		e.tokenHash = t.tokenHash();
		e.playerRef = t.playerRef();
		e.expiresAt = t.expiresAt();
		e.usedAt = t.usedAt();
		e.createdAt = t.createdAt();
		return e;
	}

	GameToken toDomain() {
		return new GameToken(id, gameId, userId, tokenHash, playerRef, expiresAt, usedAt, createdAt);
	}
}

interface SpringDataGameTokenRepository
		extends org.springframework.data.repository.Repository<GameTokenJpaEntity, String> {

	GameTokenJpaEntity save(GameTokenJpaEntity entity);

	Optional<GameTokenJpaEntity> findByTokenHash(String tokenHash);
}

@Repository
@Transactional(propagation = Propagation.REQUIRED)
class GameTokenPersistenceAdapter implements GameIdentityService.GameTokenRepository {

	private final SpringDataGameTokenRepository tokens;

	GameTokenPersistenceAdapter(SpringDataGameTokenRepository tokens) {
		this.tokens = tokens;
	}

	@Override
	public void save(GameToken token) {
		tokens.save(GameTokenJpaEntity.from(token));
	}

	@Override
	public Optional<GameToken> findByHash(String hash) {
		return tokens.findByTokenHash(hash).map(GameTokenJpaEntity::toDomain);
	}
}
