package com.socialcoisa.rede.cosmetics.adapters.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import com.socialcoisa.rede.cosmetics.application.port.out.CommunityCosmeticRepository;
import com.socialcoisa.rede.cosmetics.domain.CommunityCosmetic;
import com.socialcoisa.rede.cosmetics.domain.CommunityCosmeticStatus;

@Repository
class CommunityCosmeticPersistenceAdapter implements CommunityCosmeticRepository {

	private final SpringDataCommunityCosmeticJpaRepository cosmetics;

	CommunityCosmeticPersistenceAdapter(SpringDataCommunityCosmeticJpaRepository cosmetics) {
		this.cosmetics = cosmetics;
	}

	@Override
	public CommunityCosmetic save(CommunityCosmetic cosmetic) {
		return cosmetics.save(CommunityCosmeticJpaEntity.from(cosmetic)).toDomain();
	}

	@Override
	public Optional<CommunityCosmetic> findById(String id) {
		return cosmetics.findById(id).map(CommunityCosmeticJpaEntity::toDomain);
	}

	@Override
	public List<CommunityCosmetic> findByCreator(String creatorId, int limit) {
		return cosmetics.findTop50ByCreatorIdOrderByUpdatedAtDesc(creatorId).stream()
			.limit(Math.max(1, limit)).map(CommunityCosmeticJpaEntity::toDomain).toList();
	}

	@Override
	public List<CommunityCosmetic> findByStatus(CommunityCosmeticStatus status, int limit) {
		return cosmetics.findByStatusOrderByUpdatedAtDesc(status, PageRequest.of(0, Math.max(1, limit))).stream()
			.map(CommunityCosmeticJpaEntity::toDomain).toList();
	}
}
