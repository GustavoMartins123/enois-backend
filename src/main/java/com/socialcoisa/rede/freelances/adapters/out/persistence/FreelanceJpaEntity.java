package com.socialcoisa.rede.freelances.adapters.out.persistence;

import java.time.Instant;
import java.time.LocalDate;

import com.socialcoisa.rede.freelances.domain.Freelance;

import jakarta.persistence.*;
import java.time.*;
@Entity
@Table(name = "freelances", indexes = {
	@Index(name = "idx_freelances_status_created", columnList = "status, created_at"),
	@Index(name = "idx_freelances_user", columnList = "user_id, created_at") })
class FreelanceJpaEntity {
	@Id private String id;
	@Column(name="user_id", nullable=false) private String userId;
	@Column(nullable=false, length=200) private String title;
	@Column(nullable=false, length=4000) private String scope;
	@Column(name="budget_min", nullable=false) private int budgetMin;
	@Column(name="budget_max", nullable=false) private int budgetMax;
	@Column(nullable=false) private LocalDate deadline;
	@Enumerated(EnumType.STRING) @Column(name="payment_method", nullable=false, length=16)
	private Freelance.PaymentMethod paymentMethod;
	@Enumerated(EnumType.STRING) @Column(nullable=false, length=16) private Freelance.Status status;
	@Column(name="created_at", nullable=false) private Instant createdAt;
	@Column(name="updated_at", nullable=false) private Instant updatedAt;

	protected FreelanceJpaEntity() {}

	static FreelanceJpaEntity fromDomain(Freelance f) {
		FreelanceJpaEntity e = new FreelanceJpaEntity();
		e.id=f.id(); e.userId=f.userId(); e.title=f.title(); e.scope=f.scope();
		e.budgetMin=f.budgetMin(); e.budgetMax=f.budgetMax(); e.deadline=f.deadline();
		e.paymentMethod=f.paymentMethod();
		e.status=f.status(); e.createdAt=f.createdAt(); e.updatedAt=f.updatedAt();
		return e;
	}

	Freelance toDomain() {
		return Freelance.restore(id,userId,title,scope,budgetMin,budgetMax,deadline,paymentMethod,status,
			createdAt,updatedAt);
	}
}
