package com.socialcoisa.rede.feed.application;

import java.time.Instant;
import java.util.List;

import com.socialcoisa.rede.feed.domain.Counters;
import com.socialcoisa.rede.feed.domain.Post;
import com.socialcoisa.rede.feed.domain.PostType;
import com.socialcoisa.rede.feed.domain.ReactionType;

public record PostView(String id, String authorId, String authorUsername, String authorName, String authorAvatar,
		PostType type, String content, String pageHtml, List<String> tags, List<String> mediaIds, Instant createdAt,
		Counters counters, ReactionType currentUserReaction, String communityId, CommunityView community,
		Instant editedAt) {

	/** Comunidade onde o post foi publicado (nome para o texto "publicou em ...") + se quem le participa. */
	public record CommunityView(String id, String name, boolean joined) {
	}

	/**
	 * Post sem o corpo da pagina: e o formato de listagem, de feed e das respostas de publicacao e
	 * edicao. A pagina de um post do tipo PAGE pode ser grande (dezenas de milhares de caracteres)
	 * e nao tem por que viajar em toda listagem — quem precisa dela pede o post unico, que anexa o
	 * HTML via {@link #withPageHtml(String)}.
	 *
	 * Ha um motivo concreto alem do tamanho: a camada de idempotencia guarda o corpo da resposta
	 * das escritas, e um HTML grande nao cabe nesse registro (a escrita chega a falhar depois de o
	 * post ja ter sido gravado). Devolver so o essencial mantem essas respostas pequenas.
	 */
	public static PostView from(Post post) {
		return new PostView(post.id().value(), post.authorId().value(), null, null, null, post.type(), post.content(),
			null, post.tags(), post.mediaIds(), post.createdAt(), post.counters(), null, post.communityId(), null,
			post.editedAt());
	}

	public static PostView from(Post post, String authorUsername, String authorName, String authorAvatar) {
		return new PostView(post.id().value(), post.authorId().value(), authorUsername, authorName, authorAvatar,
			post.type(), post.content(), null, post.tags(), post.mediaIds(), post.createdAt(), post.counters(), null,
			post.communityId(), null, post.editedAt());
	}

	public PostView withCurrentUserReaction(ReactionType reaction) {
		return new PostView(id, authorId, authorUsername, authorName, authorAvatar, type, content, pageHtml, tags,
			mediaIds, createdAt, counters, reaction, communityId, community, editedAt);
	}

	/** Anexa o HTML da pagina; so o post unico faz isso (ver {@link #from(Post)}). */
	public PostView withPageHtml(String pageHtml) {
		return new PostView(id, authorId, authorUsername, authorName, authorAvatar, type, content, pageHtml, tags,
			mediaIds, createdAt, counters, currentUserReaction, communityId, community, editedAt);
	}

	/** Anexa a comunidade (nome + participacao do leitor) depois do enriquecimento. */
	public PostView withCommunity(String communityId, CommunityView community) {
		return new PostView(id, authorId, authorUsername, authorName, authorAvatar, type, content, pageHtml, tags,
			mediaIds, createdAt, counters, currentUserReaction, communityId, community, editedAt);
	}
}
