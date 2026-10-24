package com.socialcoisa.rede.contributors.domain;

import java.time.Instant;

/**
 * Registro de quem se ofereceu para contribuir com o projeto. O formulário é público, mas o
 * registro é interno: serve para organizar a fila, documentar o aceite dos termos e não perder
 * contato com quem se apresentou.
 */
public class ContributorApplication {

	private final String id;
	private final String name;
	private final String github;
	private final String email;
	private final String area;
	private final ContributorExperience experience;
	private final String message;
	private final Instant acceptedTermsAt;
	private final ContributorApplicationStatus status;
	private final Instant createdAt;

	private ContributorApplication(String id, String name, String github, String email, String area,
			ContributorExperience experience, String message, Instant acceptedTermsAt,
			ContributorApplicationStatus status, Instant createdAt) {
		this.id = id;
		this.name = name;
		this.github = github;
		this.email = email;
		this.area = area;
		this.experience = experience;
		this.message = message;
		this.acceptedTermsAt = acceptedTermsAt;
		this.status = status;
		this.createdAt = createdAt;
	}

	public static ContributorApplication create(String id, String name, String github, String email, String area,
			ContributorExperience experience, String message, Instant now) {
		return new ContributorApplication(id, name, github, email, area, experience, message, now,
			ContributorApplicationStatus.NEW, now);
	}

	public static ContributorApplication restore(String id, String name, String github, String email, String area,
			ContributorExperience experience, String message, Instant acceptedTermsAt,
			ContributorApplicationStatus status, Instant createdAt) {
		return new ContributorApplication(id, name, github, email, area, experience, message, acceptedTermsAt, status,
			createdAt);
	}

	public ContributorApplication withStatus(ContributorApplicationStatus newStatus) {
		return new ContributorApplication(id, name, github, email, area, experience, message, acceptedTermsAt,
			newStatus, createdAt);
	}

	public String id() {
		return id;
	}

	public String name() {
		return name;
	}

	public String github() {
		return github;
	}

	public String email() {
		return email;
	}

	public String area() {
		return area;
	}

	public ContributorExperience experience() {
		return experience;
	}

	public String message() {
		return message;
	}

	public Instant acceptedTermsAt() {
		return acceptedTermsAt;
	}

	public ContributorApplicationStatus status() {
		return status;
	}

	public Instant createdAt() {
		return createdAt;
	}
}
