package com.socialcoisa.rede.games.adapters.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.socialcoisa.rede.games.application.port.out.DeveloperProfileRepository;
import com.socialcoisa.rede.games.domain.DeveloperProfile;

@Repository
class DeveloperProfilePersistenceAdapter implements DeveloperProfileRepository {

	private final SpringDataDeveloperProfileJpaRepository profiles;

	DeveloperProfilePersistenceAdapter(SpringDataDeveloperProfileJpaRepository profiles) {
		this.profiles = profiles;
	}

	@Override
	public DeveloperProfile save(DeveloperProfile profile) {
		return profiles.save(DeveloperProfileJpaEntity.fromDomain(profile)).toDomain();
	}

	@Override
	public Optional<DeveloperProfile> findByUserId(String userId) {
		return profiles.findById(userId).map(DeveloperProfileJpaEntity::toDomain);
	}

	@Override
	public List<DeveloperProfile> findAllByUserIds(Collection<String> userIds) {
		if (userIds == null || userIds.isEmpty()) {
			return List.of();
		}
		return profiles.findByUserIdIn(userIds).stream().map(DeveloperProfileJpaEntity::toDomain).toList();
	}
}
