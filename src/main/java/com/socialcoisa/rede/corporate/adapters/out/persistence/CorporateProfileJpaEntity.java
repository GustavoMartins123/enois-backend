package com.socialcoisa.rede.corporate.adapters.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.socialcoisa.rede.corporate.domain.CorporateProfile;
import com.socialcoisa.rede.corporate.domain.CorporateProfileStatus;

@Entity
@Table(name = "corporate_profiles")
class CorporateProfileJpaEntity {

	@Id
	@Column(name = "user_id", nullable = false)
	private String userId;

	@Column(name = "username", nullable = false)
	private String username;

	@Column(name = "name", nullable = false)
	private String name;

	@Column(name = "headline")
	private String headline;

	@Column(name = "bio")
	private String bio;

	@Column(name = "avatar")
	private String avatar;

	@Column(name = "banner")
	private String banner;

	@Column(name = "banner_html", columnDefinition = "longtext")
	private String bannerHtml;

	@Column(name = "location")
	private String location;

	@Column(name = "industry")
	private String industry;

	@Column(name = "external_links")
	private String externalLinks;

	@Column(name = "status", nullable = false)
	private String status;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected CorporateProfileJpaEntity() {
	}

	static CorporateProfileJpaEntity fromDomain(CorporateProfile profile) {
		CorporateProfileJpaEntity entity = new CorporateProfileJpaEntity();
		entity.userId = profile.userId();
		entity.username = profile.username();
		entity.name = profile.name();
		entity.headline = profile.headline();
		entity.bio = profile.bio();
		entity.avatar = profile.avatar();
		entity.banner = profile.banner();
		entity.bannerHtml = profile.bannerHtml();
		entity.location = profile.location();
		entity.industry = profile.industry();
		entity.externalLinks = profile.externalLinks();
		entity.status = profile.status().name();
		entity.createdAt = profile.createdAt();
		entity.updatedAt = profile.updatedAt();
		return entity;
	}

	CorporateProfile toDomain() {
		return new CorporateProfile(userId, username, name, headline, bio, avatar, banner, bannerHtml, location, industry,
			externalLinks, CorporateProfileStatus.valueOf(status), createdAt, updatedAt);
	}
}
