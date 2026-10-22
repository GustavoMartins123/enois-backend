package com.socialcoisa.rede.cosmetics.domain;

import java.time.Instant;

import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Visual criado pela comunidade. O manifest e declarativo: CSS, JS e HTML livre ficam fora
 * deste dominio e serao rejeitados pela porta de validacao.
 */
public class CommunityCosmetic {

	private final String id;
	private final String creatorId;
	private final CommunityCosmeticKind kind;
	private final String name;
	private final String description;
	private final String manifestJson;
	private final String license;
	private final boolean rightsConfirmed;
	private final CommunityCosmeticStatus status;
	private final int version;
	private final int reported;
	private final Instant createdAt;
	private final Instant updatedAt;

	private CommunityCosmetic(String id, String creatorId, CommunityCosmeticKind kind, String name,
		String description, String manifestJson, String license, boolean rightsConfirmed,
		CommunityCosmeticStatus status, int version, int reported, Instant createdAt, Instant updatedAt) {
		this.id = id;
		this.creatorId = creatorId;
		this.kind = kind;
		this.name = name;
		this.description = description;
		this.manifestJson = manifestJson;
		this.license = license;
		this.rightsConfirmed = rightsConfirmed;
		this.status = status;
		this.version = version;
		this.reported = reported;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	public static CommunityCosmetic create(String id, String creatorId, CommunityCosmeticKind kind, String name,
		String description, String manifestJson, String license, boolean rightsConfirmed, Instant now) {
		return new CommunityCosmetic(id, creatorId, kind, name, description, manifestJson, license, rightsConfirmed,
			CommunityCosmeticStatus.DRAFT, 1, 0, now, now);
	}

	public static CommunityCosmetic restore(String id, String creatorId, CommunityCosmeticKind kind, String name,
		String description, String manifestJson, String license, boolean rightsConfirmed,
		CommunityCosmeticStatus status, int version, int reported, Instant createdAt, Instant updatedAt) {
		return new CommunityCosmetic(id, creatorId, kind, name, description, manifestJson, license, rightsConfirmed,
			status, version, reported, createdAt, updatedAt);
	}

	public CommunityCosmetic edit(String name, String description, String manifestJson, String license,
		boolean rightsConfirmed, Instant now) {
		CommunityCosmeticStatus nextStatus = status == CommunityCosmeticStatus.REJECTED
			? CommunityCosmeticStatus.DRAFT : status;
		return new CommunityCosmetic(id, creatorId, kind, name != null ? name : this.name,
			description != null ? (description.isBlank() ? null : description) : this.description,
			manifestJson != null ? manifestJson : this.manifestJson, license != null ? license : this.license,
			rightsConfirmed, nextStatus, version + 1, reported, createdAt, now);
	}

	public CommunityCosmetic submit(Instant now) {
		if (status != CommunityCosmeticStatus.DRAFT) {
			throw new ApiException(409, "COSMETIC_NOT_EDITABLE", "Só um rascunho pode ser enviado para moderação.");
		}
		return new CommunityCosmetic(id, creatorId, kind, name, description, manifestJson, license, rightsConfirmed,
			CommunityCosmeticStatus.PENDING_MODERATION, version, reported, createdAt, now);
	}

	public CommunityCosmetic report(Instant now) {
		return new CommunityCosmetic(id, creatorId, kind, name, description, manifestJson, license, rightsConfirmed,
			status, version, reported + 1, createdAt, now);
	}

	public boolean ownedBy(String userId) {
		return creatorId != null && creatorId.equals(userId);
	}

	public String id() { return id; }
	public String creatorId() { return creatorId; }
	public CommunityCosmeticKind kind() { return kind; }
	public String name() { return name; }
	public String description() { return description; }
	public String manifestJson() { return manifestJson; }
	public String license() { return license; }
	public boolean rightsConfirmed() { return rightsConfirmed; }
	public CommunityCosmeticStatus status() { return status; }
	public int version() { return version; }
	public int reported() { return reported; }
	public Instant createdAt() { return createdAt; }
	public Instant updatedAt() { return updatedAt; }
}
