package com.socialcoisa.rede.jobs.adapters.out.persistence;

import java.time.Instant;

import com.socialcoisa.rede.jobs.domain.ExternalJob;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "external_jobs",
	uniqueConstraints = @UniqueConstraint(name = "uk_external_jobs_source",
		columnNames = { "platform", "external_id" }),
	indexes = @Index(name = "idx_external_jobs_checked", columnList = "last_checked_at"))
class ExternalJobJpaEntity {

	@Id
	private String id;

	@Column(nullable = false)
	private String platform;

	@Column(name = "external_id", nullable = false)
	private String externalId;

	@Column(nullable = false, length = 500)
	private String title;

	private String company;

	private String location;

	private String salary;

	@Column(length = 8)
	private String currency;

	@Column(nullable = false, length = 1000)
	private String url;

	@Column(name = "imported_at", nullable = false)
	private Instant importedAt;

	@Column(name = "last_checked_at", nullable = false)
	private Instant lastCheckedAt;

	@Column(name = "external_status", nullable = false)
	private String externalStatus = ExternalJob.FOUND;

	@Column(name = "canonical_url", length = 1000)
	private String canonicalUrl;

	@Column(name = "apply_email", length = 320)
	private String applyEmail;

	/** Autor do cadastro manual (nulo nas importadas de agregador). */
	@Column(name = "submitted_by")
	private String submittedBy;

	protected ExternalJobJpaEntity() {
	}

	static ExternalJobJpaEntity fromDomain(ExternalJob job) {
		ExternalJobJpaEntity e = new ExternalJobJpaEntity();
		e.id = job.id();
		e.platform = job.platform();
		e.externalId = job.externalId();
		e.title = job.title();
		e.company = job.company();
		e.location = job.location();
		e.salary = job.salary();
		e.currency = job.currency();
		e.url = job.url();
		e.importedAt = job.importedAt();
		e.lastCheckedAt = job.lastCheckedAt();
		e.externalStatus = job.status() == null ? ExternalJob.FOUND : job.status();
		e.canonicalUrl = job.canonicalUrl();
		e.applyEmail = job.applyEmail();
		e.submittedBy = job.submittedBy();
		return e;
	}

	ExternalJob toDomain() {
		return new ExternalJob(id, platform, externalId, title, company, location, salary, currency, url,
			importedAt, lastCheckedAt, externalStatus, canonicalUrl, applyEmail, submittedBy);
	}
}
