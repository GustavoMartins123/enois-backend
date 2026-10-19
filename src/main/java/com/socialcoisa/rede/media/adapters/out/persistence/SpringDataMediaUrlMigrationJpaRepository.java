package com.socialcoisa.rede.media.adapters.out.persistence;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataMediaUrlMigrationJpaRepository extends JpaRepository<MediaUrlMigrationJpaEntity, String> {

	@Modifying
	@Query("update MediaUrlMigrationJpaEntity e set e.claimedAt = :now, e.attempts = e.attempts + 1 "
		+ "where e.urlHash = :hash and e.status in ('PENDING', 'FAILED') and e.attempts < :maxAttempts "
		+ "and (e.claimedAt is null or e.claimedAt < :staleBefore)")
	int claim(@Param("hash") String hash, @Param("now") Instant now, @Param("staleBefore") Instant staleBefore,
		@Param("maxAttempts") int maxAttempts);

	@Query("select e from MediaUrlMigrationJpaEntity e where e.status = 'COPIED' order by e.updatedAt asc")
	List<MediaUrlMigrationJpaEntity> findForFlip(Pageable pageable);
}
