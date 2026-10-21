package com.socialcoisa.rede.jobs.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataJobAbandonPenaltyJpaRepository extends JpaRepository<JobAbandonPenaltyJpaEntity, String> {

	int countByUserId(String userId);
}
