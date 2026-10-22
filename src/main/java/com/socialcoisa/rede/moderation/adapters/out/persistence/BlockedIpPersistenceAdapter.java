package com.socialcoisa.rede.moderation.adapters.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import com.socialcoisa.rede.moderation.application.port.out.BlockedIpRepository;
import com.socialcoisa.rede.moderation.domain.BlockedIp;

@Component
class BlockedIpPersistenceAdapter implements BlockedIpRepository {

	private final SpringDataBlockedIpJpaRepository repository;

	BlockedIpPersistenceAdapter(SpringDataBlockedIpJpaRepository repository) {
		this.repository = repository;
	}

	@Override
	public BlockedIp save(BlockedIp blockedIp) {
		return repository.save(new BlockedIpJpaEntity(blockedIp.id(), blockedIp.ip(), blockedIp.reason(),
			blockedIp.createdBy(), blockedIp.createdAt())).toDomain();
	}

	@Override
	public Optional<BlockedIp> findByIp(String ip) {
		return repository.findByIp(ip).map(BlockedIpJpaEntity::toDomain);
	}

	@Override
	public boolean existsByIp(String ip) {
		return repository.existsByIp(ip);
	}

	@Override
	public List<BlockedIp> findRecent(int limit) {
		Pageable page = PageRequest.of(0, Math.max(1, limit));
		return repository.findByOrderByCreatedAtDesc(page).stream().map(BlockedIpJpaEntity::toDomain).toList();
	}

	@Override
	public void deleteByIp(String ip) {
		repository.deleteByIp(ip);
	}
}
