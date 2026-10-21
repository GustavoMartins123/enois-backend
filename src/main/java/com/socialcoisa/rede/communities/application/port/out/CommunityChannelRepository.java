package com.socialcoisa.rede.communities.application.port.out;

import java.util.List;
import java.util.Optional;

import com.socialcoisa.rede.communities.domain.CommunityChannel;

public interface CommunityChannelRepository {

	void save(CommunityChannel channel);

	List<CommunityChannel> findByCommunity(String communityId);

	Optional<CommunityChannel> findById(String channelId);

	boolean existsByCommunityAndSlug(String communityId, String slug);

	int countByCommunity(String communityId);
}
