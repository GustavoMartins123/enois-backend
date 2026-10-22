package com.socialcoisa.rede.ops.adapters.out.persistence;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataEmailLogJpaRepository extends JpaRepository<EmailLogJpaEntity, String> {

	List<EmailLogJpaEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
