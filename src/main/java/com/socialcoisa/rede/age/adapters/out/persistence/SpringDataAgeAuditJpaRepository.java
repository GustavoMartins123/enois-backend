package com.socialcoisa.rede.age.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataAgeAuditJpaRepository extends JpaRepository<AgeAuditJpaEntity, String> {
}
