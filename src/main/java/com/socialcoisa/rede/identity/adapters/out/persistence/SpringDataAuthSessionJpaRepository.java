package com.socialcoisa.rede.identity.adapters.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataAuthSessionJpaRepository extends JpaRepository<AuthSessionJpaEntity, String> {

	Optional<AuthSessionJpaEntity> findByRefreshToken(String refreshToken);

	@Modifying
	@Query("UPDATE AuthSessionJpaEntity s SET s.revokedAt = :now WHERE s.userId = :userId AND s.revokedAt IS NULL")
	void revokeAllByUser(@Param("userId") String userId, @Param("now") Instant now);

	@org.springframework.data.jpa.repository.Query(
		"SELECT s.ip FROM AuthSessionJpaEntity s WHERE s.userId = :userId AND s.ip IS NOT NULL "
			+ "ORDER BY s.createdAt DESC")
	List<String> findRecentIpsByUser(@Param("userId") String userId,
			org.springframework.data.domain.Pageable page);
}
