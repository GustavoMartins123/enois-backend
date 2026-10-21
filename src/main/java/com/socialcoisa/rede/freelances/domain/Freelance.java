package com.socialcoisa.rede.freelances.domain;

import java.time.Instant;
import java.time.LocalDate;

/** Trabalho pontual com escopo fechado: valor (faixa), prazo e o que será entregue. */
public class Freelance {

	public enum Status { OPEN, CLOSED }

	private final String id;
	private final String userId;
	private final String title;
	private final String scope;
	private final int budgetMin;
	private final int budgetMax;
	private final LocalDate deadline;
	private final Status status;
	private final Instant createdAt;
	private final Instant updatedAt;

	private Freelance(String id, String userId, String title, String scope, int budgetMin, int budgetMax,
			LocalDate deadline, Status status, Instant createdAt, Instant updatedAt) {
		this.id = id; this.userId = userId; this.title = title; this.scope = scope;
		this.budgetMin = budgetMin; this.budgetMax = budgetMax; this.deadline = deadline;
		this.status = status; this.createdAt = createdAt; this.updatedAt = updatedAt;
	}

	public static Freelance create(String id, String userId, String title, String scope, int budgetMin,
			int budgetMax, LocalDate deadline, Instant now) {
		return new Freelance(id, userId, title, scope, budgetMin, budgetMax, deadline, Status.OPEN, now, now);
	}

	public static Freelance restore(String id, String userId, String title, String scope, int budgetMin,
			int budgetMax, LocalDate deadline, Status status, Instant createdAt, Instant updatedAt) {
		return new Freelance(id, userId, title, scope, budgetMin, budgetMax, deadline, status, createdAt, updatedAt);
	}

	public Freelance close(Instant now) {
		return new Freelance(id, userId, title, scope, budgetMin, budgetMax, deadline, Status.CLOSED,
			createdAt, now);
	}

	public boolean ownedBy(String userId) { return this.userId.equals(userId); }

	public String id() { return id; }
	public String userId() { return userId; }
	public String title() { return title; }
	public String scope() { return scope; }
	public int budgetMin() { return budgetMin; }
	public int budgetMax() { return budgetMax; }
	public LocalDate deadline() { return deadline; }
	public Status status() { return status; }
	public Instant createdAt() { return createdAt; }
	public Instant updatedAt() { return updatedAt; }
}
