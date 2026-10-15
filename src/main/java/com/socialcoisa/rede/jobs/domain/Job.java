package com.socialcoisa.rede.jobs.domain;

import java.time.Instant;
import java.util.List;

public class Job {

	private final String id;
	private final String title;
	private final String company;
	private final String logo;
	private final String location;
	private final String type;
	private final String level;
	private final String salary;
	private final String currency;
	private final List<String> skills;
	private final List<JobApplicationField> applicationFields;
	private final String postedBy;
	private final Instant communityAgreedAt;
	private final Instant postedAt;
	private final Instant updatedAt;
	/** Estado do processo seletivo; comeca OPEN. */
	private JobStatus status;
	private Instant closedAt;

	private Job(String id, String title, String company, String logo, String location, String type, String level,
			String salary, String currency, List<String> skills, List<JobApplicationField> applicationFields,
			String postedBy, Instant communityAgreedAt, JobStatus status, Instant closedAt, Instant postedAt,
			Instant updatedAt) {
		this.id = id;
		this.title = title;
		this.company = company;
		this.logo = logo;
		this.location = location;
		this.type = type;
		this.level = level;
		this.salary = salary;
		this.currency = currency;
		this.skills = skills;
		this.applicationFields = List.copyOf(applicationFields);
		this.postedBy = postedBy;
		this.communityAgreedAt = communityAgreedAt;
		this.postedAt = postedAt;
		this.updatedAt = updatedAt;
		this.status = status == null ? JobStatus.OPEN : status;
		this.closedAt = closedAt;
	}

	public static Job create(String id, String title, String company, String logo, String location, String type,
			String level, String salary, String currency, List<String> skills, List<JobApplicationField> applicationFields,
			String postedBy, Instant communityAgreedAt, Instant now) {
		return new Job(id, title, company, logo, location, type, level, salary, currency, List.copyOf(skills),
			applicationFields, postedBy, communityAgreedAt, JobStatus.OPEN, null, now, now);
	}

	public static Job restore(String id, String title, String company, String logo, String location, String type,
			String level, String salary, String currency, List<String> skills, List<JobApplicationField> applicationFields,
			String postedBy, Instant communityAgreedAt, JobStatus status, Instant closedAt, Instant postedAt,
			Instant updatedAt) {
		return new Job(id, title, company, logo, location, type, level, salary, currency, skills, applicationFields,
			postedBy, communityAgreedAt, status, closedAt, postedAt, updatedAt);
	}

	public String id() { return id; }

	public String title() { return title; }

	public String company() { return company; }

	public String logo() { return logo; }

	public String location() { return location; }

	public String type() { return type; }

	public String level() { return level; }

	public String salary() { return salary; }

	/** Codigo ISO da moeda da faixa salarial (BRL, USD, EUR). */
	public String currency() { return currency; }

	/** Instante em que o autor declarou que a vaga e real e respeita as regras da comunidade. */
	public Instant communityAgreedAt() { return communityAgreedAt; }

	public List<String> skills() { return skills; }

	/** Campos que o autor definiu para o formulario de candidatura (pode ser vazio). */
	public List<JobApplicationField> applicationFields() { return applicationFields; }

	public String postedBy() { return postedBy; }

	public Instant postedAt() { return postedAt; }

	public Instant updatedAt() { return updatedAt; }

	public JobStatus status() { return status; }

	/** Quando o autor encerrou o processo seletivo (nulo enquanto aberta). */
	public Instant closedAt() { return closedAt; }

	public boolean isOpen() { return status == JobStatus.OPEN; }

	/**
	 * Encerra a vaga registrando o instante. Encerrar nao apaga nem altera candidaturas: elas
	 * continuam disponiveis para a triagem de quem publicou.
	 */
	public Job closed(Instant at) {
		return new Job(id, title, company, logo, location, type, level, salary, currency, skills,
			applicationFields, postedBy, communityAgreedAt, JobStatus.CLOSED, at, postedAt, updatedAt);
	}

}
