package com.socialcoisa.rede.companies.domain;

import java.time.Instant;

public record CompanyMember(String id, String companyId, String userId, CompanyRole role, Instant createdAt) {

	public static CompanyMember create(String id, String companyId, String userId, CompanyRole role, Instant now) {
		return new CompanyMember(id, companyId, userId, role, now);
	}
}
