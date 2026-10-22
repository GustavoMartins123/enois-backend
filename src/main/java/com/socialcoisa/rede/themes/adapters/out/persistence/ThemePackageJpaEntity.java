package com.socialcoisa.rede.themes.adapters.out.persistence;

import java.time.Instant;

import com.socialcoisa.rede.themes.domain.ThemeGalleryStatus;
import com.socialcoisa.rede.themes.domain.ThemePackage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "theme_gallery", indexes = {
		@Index(name = "idx_theme_gallery_status", columnList = "status, created_at"),
		@Index(name = "idx_theme_gallery_author", columnList = "author_id") })
class ThemePackageJpaEntity {

	@Id
	private String id;

	@Column(name = "author_id", nullable = false)
	private String authorId;

	@Column(nullable = false, length = 80)
	private String name;

	@Column(length = 300)
	private String description;

	@Column(name = "package_json", nullable = false, columnDefinition = "longtext")
	private String packageJson;

	@Column(name = "mode_default", nullable = false, length = 10)
	private String modeDefault;

	@Column(nullable = false)
	private int installs;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ThemeGalleryStatus status;

	@Column(nullable = false)
	private int reported;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected ThemePackageJpaEntity() {
	}

	static ThemePackageJpaEntity fromDomain(ThemePackage theme) {
		ThemePackageJpaEntity e = new ThemePackageJpaEntity();
		e.id = theme.id();
		e.authorId = theme.authorId();
		e.name = theme.name();
		e.description = theme.description();
		e.packageJson = theme.packageJson();
		e.modeDefault = theme.modeDefault();
		e.installs = theme.installs();
		e.status = theme.status();
		e.reported = theme.reported();
		e.createdAt = theme.createdAt();
		return e;
	}

	ThemePackage toDomain() {
		return ThemePackage.restore(id, authorId, name, description, packageJson, modeDefault, installs, status,
			reported, createdAt);
	}
}
