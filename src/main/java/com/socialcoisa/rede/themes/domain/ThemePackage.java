package com.socialcoisa.rede.themes.domain;

import java.time.Instant;

/**
 * Tema publicado na galeria da comunidade (Fase 2 do plano de temas).
 * O pacote e JSON de TOKENS validado — sem CSS livre, sem imagens (regra do plano).
 */
public class ThemePackage {

	private final String id;
	private final String authorId;
	private final String name;
	private final String description;
	private final String packageJson;
	private final String modeDefault;
	private final int installs;
	private final ThemeGalleryStatus status;
	private final int reported;
	private final Instant createdAt;

	private ThemePackage(String id, String authorId, String name, String description, String packageJson,
			String modeDefault, int installs, ThemeGalleryStatus status, int reported, Instant createdAt) {
		this.id = id;
		this.authorId = authorId;
		this.name = name;
		this.description = description;
		this.packageJson = packageJson;
		this.modeDefault = modeDefault;
		this.installs = installs;
		this.status = status;
		this.reported = reported;
		this.createdAt = createdAt;
	}

	public static ThemePackage create(String id, String authorId, String name, String description,
			String packageJson, String modeDefault, Instant now) {
		return new ThemePackage(id, authorId, name, description, packageJson, modeDefault, 0,
			ThemeGalleryStatus.PUBLISHED, 0, now);
	}

	public static ThemePackage restore(String id, String authorId, String name, String description,
			String packageJson, String modeDefault, int installs, ThemeGalleryStatus status, int reported,
			Instant createdAt) {
		return new ThemePackage(id, authorId, name, description, packageJson, modeDefault, installs, status,
			reported, createdAt);
	}

	public ThemePackage addInstall() {
		return new ThemePackage(id, authorId, name, description, packageJson, modeDefault, installs + 1, status,
			reported, createdAt);
	}

	public ThemePackage report() {
		return new ThemePackage(id, authorId, name, description, packageJson, modeDefault, installs, status,
			reported + 1, createdAt);
	}

	public ThemePackage hide() {
		return new ThemePackage(id, authorId, name, description, packageJson, modeDefault, installs,
			ThemeGalleryStatus.HIDDEN, reported, createdAt);
	}

	public boolean isOwnedBy(String userId) {
		return authorId.equals(userId);
	}

	public String id() { return id; }
	public String authorId() { return authorId; }
	public String name() { return name; }
	public String description() { return description; }
	public String packageJson() { return packageJson; }
	public String modeDefault() { return modeDefault; }
	public int installs() { return installs; }
	public ThemeGalleryStatus status() { return status; }
	public int reported() { return reported; }
	public Instant createdAt() { return createdAt; }
}
