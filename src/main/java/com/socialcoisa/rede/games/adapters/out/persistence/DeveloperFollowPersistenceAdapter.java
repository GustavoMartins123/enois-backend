package com.socialcoisa.rede.games.adapters.out.persistence;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.socialcoisa.rede.games.application.port.out.DeveloperFollowRepository;

@Repository
class DeveloperFollowPersistenceAdapter implements DeveloperFollowRepository {

	private final SpringDataDeveloperFollowJpaRepository follows;

	DeveloperFollowPersistenceAdapter(SpringDataDeveloperFollowJpaRepository follows) {
		this.follows = follows;
	}

	@Override
	public boolean exists(String followerId, String developerUserId) {
		return follows.existsByFollowerIdAndDeveloperUserId(followerId, developerUserId);
	}

	@Override
	public void follow(String id, String followerId, String developerUserId, Instant now) {
		follows.save(DeveloperFollowJpaEntity.of(id, followerId, developerUserId, now));
	}

	@Override
	public int unfollow(String followerId, String developerUserId) {
		return follows.deleteByPair(followerId, developerUserId);
	}

	@Override
	public long countByDeveloper(String developerUserId) {
		return follows.countByDeveloperUserId(developerUserId);
	}

	@Override
	public List<String> followedBy(String followerId) {
		return follows.findByFollowerId(followerId).stream().map(DeveloperFollowJpaEntity::developerUserId).toList();
	}

	@Override
	public List<String> followersOf(String developerUserId) {
		return follows.findByDeveloperUserId(developerUserId).stream()
			.map(DeveloperFollowJpaEntity::developerUserId).toList();
	}
}
