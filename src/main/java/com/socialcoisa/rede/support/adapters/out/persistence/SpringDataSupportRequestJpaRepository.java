package com.socialcoisa.rede.support.adapters.out.persistence;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.socialcoisa.rede.support.domain.SupportStatus;

interface SpringDataSupportRequestJpaRepository extends JpaRepository<SupportRequestJpaEntity, String> {

	List<SupportRequestJpaEntity> findByStatusOrderByCreatedAtDesc(SupportStatus status, Pageable pageable);

	long countByUserIdAndCreatedAtAfter(String userId, java.time.Instant since);
}
