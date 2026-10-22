package com.socialcoisa.rede.network.adapters.out.persistence;

import java.time.Instant;

import com.socialcoisa.rede.network.domain.UserBlock;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "user_blocks", uniqueConstraints = {
		@UniqueConstraint(name = "uk_user_blocks_pair", columnNames = { "blocker_id", "blocked_id" }) },
		indexes = {
			@Index(name = "idx_user_blocks_blocker", columnList = "blocker_id"),
			@Index(name = "idx_user_blocks_blocked", columnList = "blocked_id") })
class UserBlockJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "blocker_id", nullable = false)
	private String blockerId;

	@Column(name = "blocked_id", nullable = false)
	private String blockedId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected UserBlockJpaEntity() {
	}

	static UserBlockJpaEntity fromDomain(UserBlock block) {
		UserBlockJpaEntity entity = new UserBlockJpaEntity();
		entity.id = block.id();
		entity.blockerId = block.blockerId();
		entity.blockedId = block.blockedId();
		entity.createdAt = block.createdAt();
		return entity;
	}

	UserBlock toDomain() {
		return UserBlock.restore(id, blockerId, blockedId, createdAt);
	}
}