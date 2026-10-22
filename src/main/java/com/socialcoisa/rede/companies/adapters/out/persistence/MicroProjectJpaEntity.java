package com.socialcoisa.rede.companies.adapters.out.persistence;

import java.time.Instant;
import java.time.LocalDate;

import com.socialcoisa.rede.companies.domain.MicroProject;
import com.socialcoisa.rede.companies.domain.MicroProjectStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "micro_projects", indexes = @Index(name = "idx_micro_projects_company", columnList = "company_id, status"))
class MicroProjectJpaEntity {

	@Id
	private String id;

	@Column(name = "company_id", nullable = false)
	private String companyId;

	@Column(name = "created_by", nullable = false)
	private String createdBy;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(nullable = false, length = 4000)
	private String problem;

	@Column(length = 4000)
	private String scope;

	@Column(length = 4000)
	private String deliverables;

	@Column(name = "acceptance_criteria", length = 4000)
	private String acceptanceCriteria;

	@Column(length = 160)
	private String budget;

	@Column(name = "deadline")
	private LocalDate deadline;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private MicroProjectStatus status;

	@Column(name = "selected_proposal_id")
	private String selectedProposalId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected MicroProjectJpaEntity() {
	}

	static MicroProjectJpaEntity fromDomain(MicroProject project) {
		MicroProjectJpaEntity e = new MicroProjectJpaEntity();
		e.id = project.id();
		e.companyId = project.companyId();
		e.createdBy = project.createdBy();
		e.title = project.title();
		e.problem = project.problem();
		e.scope = project.scope();
		e.deliverables = project.deliverables();
		e.acceptanceCriteria = project.acceptanceCriteria();
		e.budget = project.budget();
		e.deadline = project.deadline();
		e.status = project.status();
		e.selectedProposalId = project.selectedProposalId();
		e.createdAt = project.createdAt();
		e.updatedAt = project.updatedAt();
		return e;
	}

	MicroProject toDomain() {
		return MicroProject.restore(id, companyId, createdBy, title, problem, scope, deliverables,
			acceptanceCriteria, budget, deadline, status, selectedProposalId, createdAt, updatedAt);
	}
}
