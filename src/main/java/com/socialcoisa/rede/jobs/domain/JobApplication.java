package com.socialcoisa.rede.jobs.domain;

import java.time.Instant;
import java.util.List;

/**
 * Candidatura: quem se candidatou, a mensagem opcional, as respostas dos campos personalizados e um
 * retrato do perfil do candidato no momento da inscricao (o empregador precisa saber quem se
 * candidatou naquele momento — a reputacao e a experiencia de hoje podem ser outras).
 */
public class JobApplication {

	private final String id;
	private final String jobId;
	private final String userId;
	private final String message;
	private final String candidateSnapshot;
	private final Instant appliedAt;
	private final List<JobApplicationAnswer> answers;

	private JobApplication(String id, String jobId, String userId, String message, String candidateSnapshot,
			Instant appliedAt, List<JobApplicationAnswer> answers) {
		this.id = id;
		this.jobId = jobId;
		this.userId = userId;
		this.message = message;
		this.candidateSnapshot = candidateSnapshot;
		this.appliedAt = appliedAt;
		this.answers = List.copyOf(answers);
	}

	public static JobApplication create(String id, String jobId, String userId, String message, String candidateSnapshot,
			Instant appliedAt, List<JobApplicationAnswer> answers) {
		return new JobApplication(id, jobId, userId, message, candidateSnapshot, appliedAt, answers);
	}

	public static JobApplication restore(String id, String jobId, String userId, String message, String candidateSnapshot,
			Instant appliedAt, List<JobApplicationAnswer> answers) {
		return new JobApplication(id, jobId, userId, message, candidateSnapshot, appliedAt, answers);
	}

	public String id() {
		return id;
	}

	public String jobId() {
		return jobId;
	}

	public String userId() {
		return userId;
	}

	public String message() {
		return message;
	}

	public String candidateSnapshot() {
		return candidateSnapshot;
	}

	public Instant appliedAt() {
		return appliedAt;
	}

	public List<JobApplicationAnswer> answers() {
		return answers;
	}

	/** Resposta de um campo, com o rotulo congelado no momento do envio. */
	public record JobApplicationAnswer(String fieldId, String label, String value, int position) {

		public JobApplicationAnswer(String fieldId, String label, String value) {
			this(fieldId, label, value, 0);
		}
	}
}