package com.socialcoisa.rede.companies.adapters.out.persistence;

import java.time.Instant;

import com.socialcoisa.rede.companies.domain.Company;
import com.socialcoisa.rede.companies.domain.CompanyStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "companies")
class CompanyJpaEntity {

	@Id
	private String id;

	@Column(nullable = false, length = 120)
	private String slug;

	@Column(nullable = false, length = 160)
	private String name;

	@Column(length = 4000)
	private String description;

	@Column(length = 80)
	private String sector;

	@Column(length = 40)
	private String size;

	@Column(length = 160)
	private String location;

	@Column(length = 300)
	private String website;

	@Column(length = 500)
	private String logo;

	@Column(length = 500)
	private String cover;

	@Column(name = "owner_user_id", nullable = false)
	private String ownerUserId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private CompanyStatus status;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected CompanyJpaEntity() {
	}

	static CompanyJpaEntity fromDomain(Company company) {
		CompanyJpaEntity e = new CompanyJpaEntity();
		e.id = company.id();
		e.slug = company.slug();
		e.name = company.name();
		e.description = company.description();
		e.sector = company.sector();
		e.size = company.size();
		e.location = company.location();
		e.website = company.website();
		e.logo = company.logo();
		e.cover = company.cover();
		e.ownerUserId = company.ownerUserId();
		e.status = company.status();
		e.createdAt = company.createdAt();
		e.updatedAt = company.updatedAt();
		return e;
	}

	Company toDomain() {
		return Company.restore(id, slug, name, description, sector, size, location, website, logo, cover,
			ownerUserId, status, createdAt, updatedAt);
	}
}
