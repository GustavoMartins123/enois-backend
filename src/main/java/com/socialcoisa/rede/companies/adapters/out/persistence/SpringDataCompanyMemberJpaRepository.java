package com.socialcoisa.rede.companies.adapters.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataCompanyMemberJpaRepository extends JpaRepository<CompanyMemberJpaEntity, String> {

	Optional<CompanyMemberJpaEntity> findByCompanyIdAndUserId(String companyId, String userId);

	List<CompanyMemberJpaEntity> findByCompanyIdOrderByCreatedAtAsc(String companyId);
}
