package com.socialcoisa.rede.companies.adapters.out.persistence;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.socialcoisa.rede.companies.domain.MicroProjectStatus;

interface SpringDataMicroProjectJpaRepository extends JpaRepository<MicroProjectJpaEntity, String> {

	List<MicroProjectJpaEntity> findByStatusOrderByCreatedAtDesc(MicroProjectStatus status, Pageable pageable);

	List<MicroProjectJpaEntity> findTop50ByCompanyIdOrderByCreatedAtDesc(String companyId);
}
