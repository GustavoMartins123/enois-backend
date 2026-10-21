package com.socialcoisa.rede.age.adapters.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.socialcoisa.rede.age.domain.ParentalLink;

interface SpringDataParentalLinkJpaRepository extends JpaRepository<ParentalLinkJpaEntity, String> {

	Optional<ParentalLinkJpaEntity> findByTokenHash(String tokenHash);

	Optional<ParentalLinkJpaEntity> findFirstByMinorUserIdAndStatus(String minorUserId, ParentalLink.Status status);

	List<ParentalLinkJpaEntity> findByGuardianUserIdAndStatus(String guardianUserId, ParentalLink.Status status);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update ParentalLinkJpaEntity l set l.status = :revoked, l.revokedAt = :now "
		+ "where l.minorUserId = :minor and l.status = :pending")
	int cancelPendingByMinor(@Param("minor") String minorUserId, @Param("pending") ParentalLink.Status pending,
		@Param("revoked") ParentalLink.Status revoked, @Param("now") Instant now);
}
