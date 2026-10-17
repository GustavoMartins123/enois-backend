package com.socialcoisa.rede.companies.application.port.out;

import java.util.List;
import java.util.Optional;

import com.socialcoisa.rede.companies.domain.Company;
import com.socialcoisa.rede.companies.domain.CompanyMember;

public interface CompanyRepository {

	Company save(Company company);

	Optional<Company> findById(String id);

	Optional<Company> findBySlug(String slug);

	/** Paginas empresariais de um usuario (as que ele tem papel OWNER; membros veem por CompanyMemberships). */
	List<Company> findByOwner(String ownerUserId, int limit);

	boolean existsBySlug(String slug);

	List<Company> listAll(int limit);

	CompanyMember saveMember(CompanyMember member);

	Optional<CompanyMember> findMember(String companyId, String userId);

	List<CompanyMember> membersOf(String companyId);
}
