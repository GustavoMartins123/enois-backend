package com.socialcoisa.rede.feed.adapters.out.persistence;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataScheduledPostJpaRepository extends JpaRepository<ScheduledPostJpaEntity, String> {

	List<ScheduledPostJpaEntity> findByUserIdOrderByScheduledAtAsc(String userId);

	List<ScheduledPostJpaEntity> findTop20ByScheduledAtLessThanEqualAndAttemptsLessThanOrderByScheduledAtAsc(
			Instant now, int attemptsBelow);
}
