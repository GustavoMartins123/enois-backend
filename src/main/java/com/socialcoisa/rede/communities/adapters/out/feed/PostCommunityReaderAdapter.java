package com.socialcoisa.rede.communities.adapters.out.feed;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.socialcoisa.rede.communities.application.port.out.CommunityRepository;
import com.socialcoisa.rede.feed.application.port.out.PostCommunityReader;

/** Implementa a leitura que o feed precisa: nome da comunidade e se o leitor ja participa. */
@Component
class PostCommunityReaderAdapter implements PostCommunityReader {

	private final CommunityRepository communityRepository;

	PostCommunityReaderAdapter(CommunityRepository communityRepository) {
		this.communityRepository = communityRepository;
	}

	@Override
	public Map<String, CommunityRef> byIds(Collection<String> communityIds, String viewerId) {
		Map<String, CommunityRef> result = new LinkedHashMap<>();
		if (communityIds == null || communityIds.isEmpty()) {
			return result;
		}
		for (String communityId : communityIds) {
			communityRepository.findById(communityId).ifPresent(community -> result.put(communityId,
				new CommunityRef(community.id(), community.name(),
					viewerId != null && communityRepository.isMember(communityId, viewerId))));
		}
		return result;
	}
}
