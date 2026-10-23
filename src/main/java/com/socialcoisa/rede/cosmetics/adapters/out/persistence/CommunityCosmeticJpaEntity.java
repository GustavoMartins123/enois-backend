package com.socialcoisa.rede.cosmetics.adapters.out.persistence;

import java.time.Instant;

import com.socialcoisa.rede.cosmetics.domain.CommunityCosmetic;
import com.socialcoisa.rede.cosmetics.domain.CommunityCosmeticKind;
import com.socialcoisa.rede.cosmetics.domain.CommunityCosmeticStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "profile_cosmetics", indexes = {
		@Index(name = "idx_profile_cosmetics_creator", columnList = "creator_id, created_at"),
		@Index(name = "idx_profile_cosmetics_status", columnList = "status, created_at") })
class CommunityCosmeticJpaEntity {

	@Id
	@Column(length = 64, nullable = false)
	private String id;

	@Column(name = "creator_id", length = 64, nullable = false)
	private String creatorId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private CommunityCosmeticKind kind;

	@Column(nullable = false, length = 80)
	private String name;

	@Column(length = 300)
	private String description;

	@Column(name = "manifest_json", nullable = false, columnDefinition = "longtext")
	private String manifestJson;

	@Column(nullable = false, length = 40)
	private String license;

	@Column(name = "rights_confirmed", nullable = false)
	private boolean rightsConfirmed;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private CommunityCosmeticStatus status;

	@Column(nullable = false)
	private int version;

	@Column(nullable = false)
	private int reported;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected CommunityCosmeticJpaEntity() {
	}

	static CommunityCosmeticJpaEntity from(CommunityCosmetic cosmetic) {
		CommunityCosmeticJpaEntity e = new CommunityCosmeticJpaEntity();
		e.id = cosmetic.id();
		e.creatorId = cosmetic.creatorId();
		e.kind = cosmetic.kind();
		e.name = cosmetic.name();
		e.description = cosmetic.description();
		e.manifestJson = cosmetic.manifestJson();
		e.license = cosmetic.license();
		e.rightsConfirmed = cosmetic.rightsConfirmed();
		e.status = cosmetic.status();
		e.version = cosmetic.version();
		e.reported = cosmetic.reported();
		e.createdAt = cosmetic.createdAt();
		e.updatedAt = cosmetic.updatedAt();
		return e;
	}

	CommunityCosmetic toDomain() {
		return CommunityCosmetic.restore(id, creatorId, kind, name, description, manifestJson, license,
			rightsConfirmed, status, version, reported, createdAt, updatedAt);
	}
}
