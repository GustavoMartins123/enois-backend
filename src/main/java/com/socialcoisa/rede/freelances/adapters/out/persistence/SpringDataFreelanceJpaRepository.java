package com.socialcoisa.rede.freelances.adapters.out.persistence;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataFreelanceJpaRepository extends JpaRepository<FreelanceJpaEntity, String> {
	List<FreelanceJpaEntity> findByStatusOrderByCreatedAtDesc(com.socialcoisa.rede.freelances.domain.Freelance.Status status, Pageable page);
	List<FreelanceJpaEntity> findByUserIdOrderByCreatedAtDesc(String userId);
	long countByStatus(com.socialcoisa.rede.freelances.domain.Freelance.Status status);
}
