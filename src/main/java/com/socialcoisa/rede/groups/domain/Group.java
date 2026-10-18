package com.socialcoisa.rede.groups.domain;

import java.time.Instant;

/**
 * Um grupo: espaço privado com identidade, feed e administração próprios.
 *
 * A classe guarda o que o grupo É. Quem PODE o quê é {@link GroupPermissions}; o que cada estado
 * permite é {@link GroupStatus}; como se entra é {@link GroupJoinMode}. As quatro decisões ficam
 * separadas de propósito — juntas, viram um `if` gigante dentro do serviço.
 *
 * `update` segue a régua do PATCH do resto da plataforma: campo nulo mantém o valor atual; string
 * vazia limpa o que dá para limpar (cabeçalho HTML vazio volta para a foto).
 */
public class Group {

	private final String id;
	private final String slug;
	private final String name;
	private final String description;
	private final String topic;
	private final String avatar;
	private final String banner;
	/** Cabeçalho em HTML/CSS sanitizado (mesma tubulação da capa do perfil); nulo = usa a foto. */
	private final String bannerHtml;
	private final GroupJoinMode joinMode;
	private final GroupStatus status;
	private final String ownerId;
	private final Instant createdAt;
	private final Instant updatedAt;
	private final Instant suspendedAt;

	private Group(String id, String slug, String name, String description, String topic, String avatar, String banner,
			String bannerHtml, GroupJoinMode joinMode, GroupStatus status, String ownerId, Instant createdAt,
			Instant updatedAt, Instant suspendedAt) {
		this.id = id;
		this.slug = slug;
		this.name = name;
		this.description = description;
		this.topic = topic;
		this.avatar = avatar;
		this.banner = banner;
		this.bannerHtml = bannerHtml;
		this.joinMode = joinMode;
		this.status = status;
		this.ownerId = ownerId;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
		this.suspendedAt = suspendedAt;
	}

	/** Grupo nasce ATIVO e sem cabeçalho HTML — o modo de ingresso é escolha de quem cria. */
	public static Group create(String id, String slug, String name, String description, String topic, String avatar,
			String banner, GroupJoinMode joinMode, String ownerId, Instant now) {
		return new Group(id, slug, name, description, topic, avatar, banner, null, joinMode, GroupStatus.ACTIVE,
			ownerId, now, now, null);
	}

	public static Group restore(String id, String slug, String name, String description, String topic, String avatar,
			String banner, String bannerHtml, GroupJoinMode joinMode, GroupStatus status, String ownerId,
			Instant createdAt, Instant updatedAt, Instant suspendedAt) {
		return new Group(id, slug, name, description, topic, avatar, banner, bannerHtml, joinMode, status, ownerId,
			createdAt, updatedAt, suspendedAt);
	}

	/**
	 * Campo nulo mantém o valor atual; `bannerHtml` em branco limpa (volta para a foto).
	 *
	 * O branco vale como limpar porque é assim no resto da plataforma (a capa da comunidade e do
	 * perfil seguem a mesma régua): um cabeçalho de espaços não é um cabeçalho.
	 */
	public Group update(String name, String description, String topic, String avatar, String banner, String bannerHtml,
			GroupJoinMode joinMode, Instant now) {
		String finalBannerHtml = bannerHtml == null ? this.bannerHtml : (bannerHtml.isBlank() ? "" : bannerHtml);
		return new Group(id, slug, name != null ? name : this.name,
			description != null ? description : this.description, topic != null ? topic : this.topic,
			avatar != null ? avatar : this.avatar, banner != null ? banner : this.banner, finalBannerHtml,
			joinMode != null ? joinMode : this.joinMode, status, ownerId, createdAt, now, suspendedAt);
	}

	/** Troca o estado. Suspender marca quando; sair de suspenso limpa a marca. */
	public Group withStatus(GroupStatus newStatus, Instant now) {
		return new Group(id, slug, name, description, topic, avatar, banner, bannerHtml, joinMode, newStatus, ownerId,
			createdAt, now, newStatus == GroupStatus.SUSPENDED ? now : null);
	}

	/** Passa a propriedade: a partir daqui é o novo dono quem responde pelo grupo. */
	public Group withOwner(String newOwnerId, Instant now) {
		return new Group(id, slug, name, description, topic, avatar, banner, bannerHtml, joinMode, status, newOwnerId,
			createdAt, now, suspendedAt);
	}

	public boolean isOwnedBy(String userId) {
		return ownerId.equals(userId);
	}

	public String id() {
		return id;
	}

	public String slug() {
		return slug;
	}

	public String name() {
		return name;
	}

	public String description() {
		return description;
	}

	public String topic() {
		return topic;
	}

	public String avatar() {
		return avatar;
	}

	public String banner() {
		return banner;
	}

	public String bannerHtml() {
		return bannerHtml;
	}

	public GroupJoinMode joinMode() {
		return joinMode;
	}

	public GroupStatus status() {
		return status;
	}

	public String ownerId() {
		return ownerId;
	}

	public Instant createdAt() {
		return createdAt;
	}

	public Instant updatedAt() {
		return updatedAt;
	}

	public Instant suspendedAt() {
		return suspendedAt;
	}
}
