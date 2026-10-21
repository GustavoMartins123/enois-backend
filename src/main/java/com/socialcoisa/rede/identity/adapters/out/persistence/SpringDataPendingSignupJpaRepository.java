package com.socialcoisa.rede.identity.adapters.out.persistence;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataPendingSignupJpaRepository extends JpaRepository<PendingSignupJpaEntity, String> {

	Optional<PendingSignupJpaEntity> findByTokenHash(String tokenHash);

	Optional<PendingSignupJpaEntity> findByEmail(String email);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from PendingSignupJpaEntity p where p.expiresAt < :now")
	int deleteExpiredBefore(@Param("now") Instant now);
}
