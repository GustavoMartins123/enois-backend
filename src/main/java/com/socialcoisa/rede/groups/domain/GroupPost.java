package com.socialcoisa.rede.groups.domain;

import java.time.Instant;
import java.util.List;

/**
 * Uma publicação do feed PRIVADO do grupo.
 *
 * Mora em tabela própria (`group_posts`), e não na `posts` do feed principal: o isolamento é
 * estrutural — o feed global, a busca pública e os perfis não alcançam este conteúdo nem por
 * engano de consulta. A publicação sempre pertence a um grupo E a um tópico DAQUELE grupo; o
 * serviço confere o vínculo (a V81 ficou sem chave estrangeira por causa da collation de produção).
 */
public class GroupPost {

	/** Teto do texto: é o tamanho da coluna (`group_posts.content`), e o serviço recusa acima. */
	public static final int MAX_CONTENT = 1_200;

	private final String id;
	private final String groupId;
	private final String topicId;
	private final String authorId;
	private final String content;
	private final List<String> mediaIds;
	private final GroupPostStatus status;
	private final boolean pinned;
	private final int reactions;
	private final int comments;
	private final Instant createdAt;
	private final Instant updatedAt;

	private GroupPost(String id, String groupId, String topicId, String authorId, String content, List<String> mediaIds,
			GroupPostStatus status, boolean pinned, int reactions, int comments, Instant createdAt, Instant updatedAt) {
		this.id = id;
		this.groupId = groupId;
		this.topicId = topicId;
		this.authorId = authorId;
		this.content = content;
		this.mediaIds = mediaIds == null ? List.of() : List.copyOf(mediaIds);
		this.status = status;
		this.pinned = pinned;
		this.reactions = reactions;
		this.comments = comments;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	public static GroupPost publish(String id, String groupId, String topicId, String authorId, String content,
			List<String> mediaIds, Instant now) {
		return new GroupPost(id, groupId, topicId, authorId, content, mediaIds, GroupPostStatus.PUBLISHED, false, 0, 0,
			now, now);
	}

	public static GroupPost restore(String id, String groupId, String topicId, String authorId, String content,
			List<String> mediaIds, GroupPostStatus status, boolean pinned, int reactions, int comments,
			Instant createdAt, Instant updatedAt) {
		return new GroupPost(id, groupId, topicId, authorId, content, mediaIds, status, pinned, reactions, comments,
			createdAt, updatedAt);
	}

	public GroupPost remove(Instant now) {
		return new GroupPost(id, groupId, topicId, authorId, content, mediaIds, GroupPostStatus.REMOVED, pinned,
			reactions, comments, createdAt, now);
	}

	/**
	 * Contadores desnormalizados: a contagem vem das tabelas de reação/comentário, na mesma transação.
	 * O `updatedAt` NÃO muda — reagir ou comentar não edita a publicação.
	 */
	public GroupPost withCounters(long reactionCount, long commentCount) {
		return new GroupPost(id, groupId, topicId, authorId, content, mediaIds, status, pinned,
			(int) Math.min(reactionCount, Integer.MAX_VALUE), (int) Math.min(commentCount, Integer.MAX_VALUE),
			createdAt, updatedAt);
	}

	/** Fixar/desafixar não edita o texto: o `updatedAt` fica como está. */
	public GroupPost withPinned(boolean pinned) {
		return new GroupPost(id, groupId, topicId, authorId, content, mediaIds, status, pinned, reactions, comments,
			createdAt, updatedAt);
	}

	public boolean isPublished() {
		return status == GroupPostStatus.PUBLISHED;
	}

	public String id() {
		return id;
	}

	public String groupId() {
		return groupId;
	}

	public String topicId() {
		return topicId;
	}

	public String authorId() {
		return authorId;
	}

	public String content() {
		return content;
	}

	public List<String> mediaIds() {
		return mediaIds;
	}

	public GroupPostStatus status() {
		return status;
	}

	public boolean pinned() {
		return pinned;
	}

	public int reactions() {
		return reactions;
	}

	public int comments() {
		return comments;
	}

	public Instant createdAt() {
		return createdAt;
	}

	public Instant updatedAt() {
		return updatedAt;
	}
}
