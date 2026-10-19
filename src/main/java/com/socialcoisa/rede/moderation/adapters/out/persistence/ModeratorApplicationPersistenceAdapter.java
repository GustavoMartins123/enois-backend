package com.socialcoisa.rede.moderation.adapters.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import com.socialcoisa.rede.moderation.application.port.out.ModeratorApplicationRepository;
import com.socialcoisa.rede.moderation.domain.ModeratorApplication;
import com.socialcoisa.rede.moderation.domain.ModeratorApplicationStatus;

@Repository
class ModeratorApplicationPersistenceAdapter implements ModeratorApplicationRepository {

	private final SpringDataModeratorApplicationJpaRepository jpaRepository;

	ModeratorApplicationPersistenceAdapter(SpringDataModeratorApplicationJpaRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	public ModeratorApplication save(ModeratorApplication application) {
		return jpaRepository.save(ModeratorApplicationJpaEntity.fromDomain(application)).toDomain();
	}

	@Override
	public Optional<ModeratorApplication> findById(String id) {
		return jpaRepository.findById(id).map(ModeratorApplicationJpaEntity::toDomain);
	}

	@Override
	public Optional<ModeratorApplication> findLatestByUser(String userId) {
		return jpaRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)
			.map(ModeratorApplicationJpaEntity::toDomain);
	}

	@Override
	public Optional<ModeratorApplication> findApprovedByUser(String userId) {
		return jpaRepository
			.findFirstByUserIdAndStatusOrderByCreatedAtDesc(userId, ModeratorApplicationStatus.APPROVED)
			.map(ModeratorApplicationJpaEntity::toDomain);
	}

	@Override
	public boolean existsPendingByUser(String userId) {
		return jpaRepository.existsByUserIdAndStatus(userId, ModeratorApplicationStatus.PENDING);
	}

	@Override
	public List<ModeratorApplication> findByStatus(ModeratorApplicationStatus status, int limit) {
		return jpaRepository
			.findByStatusOrderByCreatedAtAsc(status, PageRequest.of(0, Math.max(1, limit)))
			.stream()
			.map(ModeratorApplicationJpaEntity::toDomain)
			.toList();
	}
}
