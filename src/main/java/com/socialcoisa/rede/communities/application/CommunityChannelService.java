package com.socialcoisa.rede.communities.application;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.socialcoisa.rede.communities.application.port.out.CommunityChannelRepository;
import com.socialcoisa.rede.communities.domain.CommunityChannel;
import com.socialcoisa.rede.preferences.domain.ContentTaxonomy;
import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Canais privados por tema dentro de uma comunidade.
 *
 * A regra de acesso é uma só: participa da comunidade, participa dos canais. Não existe inscrição
 * separada por canal nesta versão — o que muda é o recorte temático, não a audiência.
 */
@Service
public class CommunityChannelService {

	private static final int MAX_CHANNELS_PER_COMMUNITY = 30;
	private static final int MAX_NAME = 80;
	private static final int MAX_DESCRIPTION = 300;

	private final CommunityChannelRepository channels;
	private final CommunityService communities;
	private final Clock clock;

	public CommunityChannelService(CommunityChannelRepository channels, CommunityService communities, Clock clock) {
		this.channels = channels;
		this.communities = communities;
		this.clock = clock;
	}

	/** Lista os canais — só membros. Quem não participa recebe 403, nunca uma lista vazia enganosa. */
	@Transactional(readOnly = true)
	public List<ChannelView> list(String communityId, String viewerId) {
		requireMember(communityId, viewerId);
		return channels.findByCommunity(communityId).stream().map(ChannelView::from).toList();
	}

	/** Cria um canal. Qualquer membro pode criar (a comunidade é o filtro de confiança). */
	@Transactional
	public ChannelView create(String communityId, String viewerId, String name, String topic, String description) {
		requireMember(communityId, viewerId);
		if (!StringUtils.hasText(name)) {
			throw new ApiException(400, "INVALID_CHANNEL_NAME", "Dê um nome ao canal.");
		}
		String cleanName = name.trim();
		if (cleanName.length() < 2 || cleanName.length() > MAX_NAME) {
			throw new ApiException(400, "INVALID_CHANNEL_NAME",
				"O nome do canal deve ter entre 2 e " + MAX_NAME + " caracteres.");
		}
		if (description != null && description.trim().length() > MAX_DESCRIPTION) {
			throw new ApiException(400, "INVALID_CHANNEL_DESCRIPTION",
				"A descrição do canal deve ter no máximo " + MAX_DESCRIPTION + " caracteres.");
		}
		if (channels.countByCommunity(communityId) >= MAX_CHANNELS_PER_COMMUNITY) {
			throw new ApiException(409, "CHANNEL_LIMIT_REACHED",
				"Esta comunidade já atingiu o limite de canais.");
		}
		String cleanTopic = normalizeTopic(topic);
		String slug = uniqueSlug(communityId, cleanName);
		CommunityChannel channel = new CommunityChannel(
			"chan_" + UUID.randomUUID().toString().replace("-", ""), communityId, cleanName, slug, cleanTopic,
			StringUtils.hasText(description) ? description.trim() : null, viewerId, clock.instant());
		channels.save(channel);
		return ChannelView.from(channel);
	}

	/** Canal de uma comunidade para um membro (ou 403/404). Usado pelo feed do canal. */
	@Transactional(readOnly = true)
	public CommunityChannel requireChannelForMember(String communityId, String channelId, String viewerId) {
		requireMember(communityId, viewerId);
		CommunityChannel channel = channels.findById(channelId)
			.orElseThrow(() -> new ApiException(404, "CHANNEL_NOT_FOUND", "Canal não encontrado."));
		if (!channel.communityId().equals(communityId)) {
			throw new ApiException(404, "CHANNEL_NOT_FOUND", "Canal não encontrado nesta comunidade.");
		}
		return channel;
	}

	private void requireMember(String communityId, String viewerId) {
		if (!StringUtils.hasText(viewerId)) {
			throw new ApiException(401, "UNAUTHENTICATED", "Entre para acessar os canais da comunidade.");
		}
		communities.getById(communityId);
		if (!communities.isMember(communityId, viewerId)) {
			throw new ApiException(403, "NOT_A_MEMBER",
				"Os canais são privados: só quem participa da comunidade pode ver.");
		}
	}

	private String normalizeTopic(String topic) {
		if (!StringUtils.hasText(topic)) {
			return null;
		}
		String normalized = topic.trim().toUpperCase(Locale.ROOT);
		if (!ContentTaxonomy.TOPIC_SET.contains(normalized)) {
			throw new ApiException(400, "INVALID_TOPIC", "Assunto inválido: escolha um da lista.");
		}
		return normalized;
	}

	private String uniqueSlug(String communityId, String name) {
		String base = slugify(name);
		String slug = base;
		int suffix = 2;
		while (channels.existsByCommunityAndSlug(communityId, slug)) {
			slug = base + "-" + suffix++;
		}
		return slug;
	}

	/** Slug simples e estável: minúsculas, sem acento, hífens. Não serve para rota pública. */
	private String slugify(String value) {
		String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
			.replaceAll("\\p{M}", "")
			.toLowerCase(Locale.ROOT)
			.replaceAll("[^a-z0-9]+", "-")
			.replaceAll("(^-|-$)", "");
		if (normalized.isEmpty()) {
			normalized = "canal";
		}
		return normalized.length() > 80 ? normalized.substring(0, 80) : normalized;
	}

	public record ChannelView(String id, String name, String slug, String topic, String description, Instant createdAt) {
		static ChannelView from(CommunityChannel channel) {
			return new ChannelView(channel.id(), channel.name(), channel.slug(), channel.topic(),
				channel.description(), channel.createdAt());
		}
	}
}
