package com.socialcoisa.rede.feed.application.port.out;

import java.util.Optional;

/** Leitura mínima do canal que o feed precisa para publicar e montar o feed do canal. */
public interface CommunityChannelReader {

	Optional<ChannelRef> byId(String channelId);

	record ChannelRef(String id, String communityId, String name, String topic) {
	}
}
