package com.socialcoisa.rede.projects.adapters.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.socialcoisa.rede.projects.application.port.out.UserProjectRepository;
import com.socialcoisa.rede.projects.domain.UserProject;

@Repository
class UserProjectPersistenceAdapter implements UserProjectRepository {

	private final SpringDataUserProjectJpaRepository jpaRepository;

	UserProjectPersistenceAdapter(SpringDataUserProjectJpaRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	public List<UserProject> findByUser(String userId) {
		return jpaRepository.findByUserIdOrderByPositionAscCreatedAtAsc(userId).stream()
			.map(UserProjectJpaEntity::toDomain)
			.toList();
	}

	@Override
	public Optional<UserProject> findById(String id) {
		return jpaRepository.findById(id).map(UserProjectJpaEntity::toDomain);
	}

	@Override
	public UserProject save(UserProject project) {
		return jpaRepository.save(UserProjectJpaEntity.fromDomain(project)).toDomain();
	}

	@Override
	public void delete(String id) {
		jpaRepository.deleteById(id);
	}
}
