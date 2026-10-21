package com.socialcoisa.rede.jobs.adapters.out.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "job_abandon_penalties")
class JobAbandonPenaltyJpaEntity {

	@Id
	String jobId;

	@Column(name = "user_id", nullable = false)
	String userId;

	@Column(nullable = false)
	int unanswered;

	@Column(name = "recorded_at", nullable = false)
	Instant recordedAt;
}
