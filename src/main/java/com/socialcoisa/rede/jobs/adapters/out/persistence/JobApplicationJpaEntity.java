package com.socialcoisa.rede.jobs.adapters.out.persistence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "job_applications",
	uniqueConstraints = @UniqueConstraint(name = "uk_job_application", columnNames = { "job_id", "user_id" }),
	indexes = {
		@Index(name = "idx_job_applications_job", columnList = "job_id"),
		@Index(name = "idx_job_applications_user", columnList = "user_id") })
class JobApplicationJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "job_id", nullable = false)
	private String jobId;

	@Column(name = "user_id", nullable = false)
	private String userId;

	@Column(nullable = false)
	private String status;

	@Column(name = "applied_at", nullable = false)
	private Instant appliedAt;

	@Column(length = 2000)
	private String message;

	@Column(name = "candidate_snapshot", length = 4000)
	private String candidateSnapshot;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "job_application_answers", joinColumns = @JoinColumn(name = "application_id"))
	@jakarta.persistence.OrderBy("position")
	private List<JobApplicationAnswerEmbeddable> answers = new ArrayList<>();

	protected JobApplicationJpaEntity() {
	}

	static JobApplicationJpaEntity create(String jobId, String userId, Instant now) {
		JobApplicationJpaEntity e = new JobApplicationJpaEntity();
		e.jobId = jobId;
		e.userId = userId;
		e.status = "APPLIED";
		e.appliedAt = now;
		return e;
	}

	static JobApplicationJpaEntity fromDomain(com.socialcoisa.rede.jobs.domain.JobApplication application) {
		JobApplicationJpaEntity e = new JobApplicationJpaEntity();
		e.jobId = application.jobId();
		e.userId = application.userId();
		e.status = "APPLIED";
		e.appliedAt = application.appliedAt();
		e.message = application.message();
		e.candidateSnapshot = application.candidateSnapshot();
		e.answers = application.answers().stream()
			.map(JobApplicationAnswerEmbeddable::fromDomain)
			.collect(java.util.stream.Collectors.toCollection(ArrayList::new));
		return e;
	}

	Long numericId() {
		return id;
	}

	com.socialcoisa.rede.jobs.domain.JobApplication toDomain() {
		return com.socialcoisa.rede.jobs.domain.JobApplication.restore(
			id == null ? null : String.valueOf(id), jobId, userId, message, candidateSnapshot, appliedAt,
			answers.stream().map(JobApplicationAnswerEmbeddable::toDomain).toList());
	}

}
