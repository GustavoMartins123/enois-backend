package com.socialcoisa.rede.moderation.adapters.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import com.socialcoisa.rede.moderation.application.port.out.AppealRepository;
import com.socialcoisa.rede.moderation.domain.Appeal;
import com.socialcoisa.rede.moderation.domain.AppealStatus;

@Repository
class AppealPersistenceAdapter implements AppealRepository {

	private final SpringDataAppealJpaRepository appeals;

	AppealPersistenceAdapter(SpringDataAppealJpaRepository appeals) {
		this.appeals = appeals;
	}

	@Override
	public Appeal save(Appeal appeal) {
		return appeals.save(AppealJpaEntity.fromDomain(appeal)).toDomain();
	}

	@Override
	public Optional<Appeal> findById(String id) {
		return appeals.findById(id).map(AppealJpaEntity::toDomain);
	}

	@Override
	public List<Appeal> findByStatus(AppealStatus status, int limit) {
		return appeals.findByStatusOrderByCreatedAtDesc(status, PageRequest.of(0, Math.max(1, limit))).stream()
			.map(AppealJpaEntity::toDomain)
			.toList();
	}

	@Override
	public List<Appeal> findByUser(String userId, int limit) {
		return appeals.findTop20ByUserIdOrderByCreatedAtDesc(userId).stream()
			.limit(limit)
			.map(AppealJpaEntity::toDomain)
			.toList();
	}

	@Override
	public boolean existsPendingByUser(String userId) {
		return appeals.existsByUserIdAndStatus(userId, AppealStatus.PENDING);
	}
}
