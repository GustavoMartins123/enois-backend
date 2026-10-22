package com.socialcoisa.rede.preferences.adapters.out.persistence;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.socialcoisa.rede.preferences.application.port.out.UserPreferenceRepository;
import com.socialcoisa.rede.preferences.domain.UserPreference;

@Repository
class UserPreferencePersistenceAdapter implements UserPreferenceRepository {

	private final SpringDataUserPreferenceJpaRepository jpaRepository;

	UserPreferencePersistenceAdapter(SpringDataUserPreferenceJpaRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	public Optional<UserPreference> find(String userId) {
		return jpaRepository.findById(userId).map(UserPreferenceJpaEntity::toDomain);
	}

	@Override
	public void save(UserPreference preference) {
		jpaRepository.save(UserPreferenceJpaEntity.fromDomain(preference));
	}

	@Override
	public void delete(String userId) {
		jpaRepository.deleteById(userId);
	}
}
