package com.socialcoisa.rede.companies.adapters.out.persistence;

import java.time.Instant;

import com.socialcoisa.rede.companies.domain.CompanyMember;
import com.socialcoisa.rede.companies.domain.CompanyRole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "company_memberships")
class CompanyMemberJpaEntity {

	@Id
	private String id;

	@Column(name = "company_id", nullable = false)
	private String companyId;

	@Column(name = "user_id", nullable = false)
	private String userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private CompanyRole role;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected CompanyMemberJpaEntity() {
	}

	static CompanyMemberJpaEntity fromDomain(CompanyMember member) {
		CompanyMemberJpaEntity e = new CompanyMemberJpaEntity();
		e.id = member.id();
		e.companyId = member.companyId();
		e.userId = member.userId();
		e.role = member.role();
		e.createdAt = member.createdAt();
		return e;
	}

	CompanyMember toDomain() {
		return CompanyMember.create(id, companyId, userId, role, createdAt);
	}
}
