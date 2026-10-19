package com.socialcoisa.rede.feed.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class Post {

	private final PostId id;
	private final UserId authorId;
	private final PostType type;
	private final String content;
	/** Mini pagina (apenas em posts do tipo PAGE); HTML ja sanitizado, nulo nos demais tipos. */
	private final String pageHtml;
	private final List<String> tags;
	private final List<String> mediaIds;
	private final String communityId;
	private final Instant createdAt;
	private final Visibility visibility;
	private Counters counters;
	private PostStatus status;
	private Instant deletedAt;
	private Instant editedAt;
	/** Quando o post se apaga sozinho (MOMENT: +24h); null = nunca. */
	private Instant expiresAt;

	private Post(PostId id, UserId authorId, PostType type, String content, String pageHtml, List<String> tags,
			List<String> mediaIds, String communityId, Instant createdAt, Visibility visibility, Counters counters,
			PostStatus status, Instant deletedAt, Instant editedAt, Instant expiresAt) {
		this.id = id;
		this.authorId = authorId;
		this.type = type;
		this.content = content;
		this.pageHtml = pageHtml;
		this.tags = List.copyOf(tags);
		this.mediaIds = List.copyOf(mediaIds);
		this.communityId = communityId;
		this.createdAt = createdAt;
		this.visibility = visibility;
		this.counters = counters;
		this.status = status;
		this.deletedAt = deletedAt;
		this.editedAt = editedAt;
		this.expiresAt = expiresAt;
	}

	public static Post publish(PostId id, UserId authorId, PostType type, String content, String pageHtml,
			List<String> tags, List<String> mediaIds, String communityId, Instant createdAt) {
		// Momento é efêmero por definição: nasce com prazo de 24 horas e se apaga sozinho.
		Instant expiresAt = type == PostType.MOMENT ? createdAt.plus(Duration.ofHours(24)) : null;
		return new Post(id, authorId, type, content, pageHtml, tags, mediaIds, communityId, createdAt,
			Visibility.PUBLIC, Counters.zero(), PostStatus.ACTIVE, null, null, expiresAt);
	}

	public static Post restore(PostId id, UserId authorId, PostType type, String content, String pageHtml,
			List<String> tags, List<String> mediaIds, String communityId, Instant createdAt, Visibility visibility,
			Counters counters, PostStatus status, Instant deletedAt, Instant editedAt, Instant expiresAt) {
		return new Post(id, authorId, type, content, pageHtml, tags, mediaIds, communityId, createdAt, visibility,
			counters, status, deletedAt, editedAt, expiresAt);
	}

	/**
	 * Edicao do proprio post: troca conteudo, pagina e tags e marca a data. Devolve uma nova instancia
	 * porque o conteudo e imutavel; id, autor e contadores sao preservados (editar nao zera nada).
	 */
	public Post edited(String newContent, String newPageHtml, List<String> newTags, Instant editedAt) {
		return new Post(id, authorId, type, newContent, newPageHtml, newTags, mediaIds, communityId, createdAt,
			visibility, counters, status, deletedAt, editedAt, expiresAt);
	}

	public boolean isActive() {
		return status == PostStatus.ACTIVE;
	}

	public Instant expiresAt() {
		return expiresAt;
	}

	/** O prazo já venceu? (o job apaga; a leitura também pode esconder antes dele rodar) */
	public boolean expired(Instant now) {
		return expiresAt != null && !expiresAt.isAfter(now);
	}

	public boolean belongsTo(UserId userId) {
		return authorId.equals(userId);
	}

	public boolean delete(Instant deletedAt) {
		if (status == PostStatus.DELETED) {
			return false;
		}
		this.status = PostStatus.DELETED;
		this.deletedAt = deletedAt;
		return true;
	}

	/**
	 * Reativa um post excluido (desfazer da exclusao). Como a exclusao e logica, reverter e seguro:
	 * o conteudo nunca saiu do banco. So faz sentido a partir de DELETED.
	 */
	public boolean undelete() {
		if (status != PostStatus.DELETED) {
			return false;
		}
		this.status = PostStatus.ACTIVE;
		this.deletedAt = null;
		return true;
	}

	public void addReaction() {
		counters = counters.incrementReactions();
	}

	public void removeReaction() {
		counters = counters.decrementReactions();
	}

	public void addComment() {
		counters = counters.incrementComments();
	}

	public void removeComment() {
		counters = counters.decrementComments();
	}

	public void addSave() {
		counters = counters.incrementSaves();
	}

	public void removeSave() {
		counters = counters.decrementSaves();
	}

	public void addShare() {
		counters = counters.incrementShares();
	}

	public PostId id() {
		return id;
	}

	public UserId authorId() {
		return authorId;
	}

	public PostType type() {
		return type;
	}

	public String content() {
		return content;
	}

	public String pageHtml() {
		return pageHtml;
	}

	public List<String> tags() {
		return tags;
	}

	public List<String> mediaIds() {
		return mediaIds;
	}

	public String communityId() {
		return communityId;
	}

	public Instant createdAt() {
		return createdAt;
	}

	public Visibility visibility() {
		return visibility;
	}

	public Counters counters() {
		return counters;
	}

	public PostStatus status() {
		return status;
	}

	public Instant deletedAt() {
		return deletedAt;
	}

	public Instant editedAt() {
		return editedAt;
	}
}
