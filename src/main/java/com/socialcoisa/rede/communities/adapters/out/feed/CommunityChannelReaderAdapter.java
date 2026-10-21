package com.socialcoisa.rede.communities.adapters.out.feed;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.socialcoisa.rede.communities.application.port.out.CommunityChannelRepository;
import com.socialcoisa.rede.feed.application.port.out.CommunityChannelReader;

/** O feed publica em canal e monta o feed do canal sem conhecer o módulo de comunidades. */
@Component
class CommunityChannelReaderAdapter implements CommunityChannelReader {

	private final CommunityChannelRepository channels;

	CommunityChannelReaderAdapter(CommunityChannelRepository channels) {
		this.channels = channels;
	}

	@Override
	public Optional<ChannelRef> byId(String channelId) {
		return channels.findById(channelId)
			.map(channel -> new ChannelRef(channel.id(), channel.communityId(), channel.name(), channel.topic()));
	}
}
