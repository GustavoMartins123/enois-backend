package com.socialcoisa.rede.identity.adapters.out.persistence;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataEmailVerificationTokenJpaRepository
		extends JpaRepository<EmailVerificationTokenJpaEntity, String> {

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update EmailVerificationTokenJpaEntity t set t.usedAt = :now "
		+ "where t.userId = :userId and t.usedAt is null")
	int markAllUsedByUserId(@Param("userId") String userId, @Param("now") Instant now);

	int countByUserIdAndCreatedAtAfter(String userId, Instant since);
}