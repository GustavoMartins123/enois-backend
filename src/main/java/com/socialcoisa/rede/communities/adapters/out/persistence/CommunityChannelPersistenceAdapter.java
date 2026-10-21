package com.socialcoisa.rede.communities.adapters.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.socialcoisa.rede.communities.application.port.out.CommunityChannelRepository;
import com.socialcoisa.rede.communities.domain.CommunityChannel;

@Repository
class CommunityChannelPersistenceAdapter implements CommunityChannelRepository {

	private final SpringDataCommunityChannelJpaRepository repository;

	CommunityChannelPersistenceAdapter(SpringDataCommunityChannelJpaRepository repository) {
		this.repository = repository;
	}

	@Override
	public void save(CommunityChannel channel) {
		repository.save(CommunityChannelJpaEntity.fromDomain(channel));
	}

	@Override
	public List<CommunityChannel> findByCommunity(String communityId) {
		return repository.findByCommunityIdOrderByCreatedAtAsc(communityId).stream()
			.map(CommunityChannelJpaEntity::toDomain)
			.toList();
	}

	@Override
	public Optional<CommunityChannel> findById(String channelId) {
		return repository.findById(channelId).map(CommunityChannelJpaEntity::toDomain);
	}

	@Override
	public boolean existsByCommunityAndSlug(String communityId, String slug) {
		return repository.existsByCommunityIdAndSlug(communityId, slug);
	}

	@Override
	public int countByCommunity(String communityId) {
		return repository.countByCommunityId(communityId);
	}
}
