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
	/** Estado no processo seletivo; comeca APPLIED e so muda pelas transicoes do JobApplicationStatus. */
	private final JobApplicationStatus status;

	private JobApplication(String id, String jobId, String userId, String message, String candidateSnapshot,
			Instant appliedAt, List<JobApplicationAnswer> answers, JobApplicationStatus status) {
		this.id = id;
		this.jobId = jobId;
		this.userId = userId;
		this.message = message;
		this.candidateSnapshot = candidateSnapshot;
		this.appliedAt = appliedAt;
		this.answers = List.copyOf(answers);
		this.status = status == null ? JobApplicationStatus.APPLIED : status;
	}

	public static JobApplication create(String id, String jobId, String userId, String message, String candidateSnapshot,
			Instant appliedAt, List<JobApplicationAnswer> answers) {
		return new JobApplication(id, jobId, userId, message, candidateSnapshot, appliedAt, answers,
			JobApplicationStatus.APPLIED);
	}

	public static JobApplication restore(String id, String jobId, String userId, String message, String candidateSnapshot,
			Instant appliedAt, List<JobApplicationAnswer> answers, JobApplicationStatus status) {
		return new JobApplication(id, jobId, userId, message, candidateSnapshot, appliedAt, answers, status);
	}

	/**
	 * Move a candidatura de estado. Recusa transicao invalida aqui, nao no controller: o dominio e
	 * quem sabe a regra, e assim nenhum caminho novo consegue gravar um estado arbitrario.
	 */
	public JobApplication withStatus(JobApplicationStatus next) {
		if (!status.canMoveTo(next)) {
			throw new com.socialcoisa.rede.shared.web.ApiException(409, "INVALID_STATUS_TRANSITION",
				"A candidatura nao pode passar de " + status + " para " + next + ".");
		}
		return new JobApplication(id, jobId, userId, message, candidateSnapshot, appliedAt, answers, next);
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

	public JobApplicationStatus status() {
		return status;
	}

	/** Resposta de um campo, com o rotulo congelado no momento do envio. */
	public record JobApplicationAnswer(String fieldId, String label, String value, int position) {

		public JobApplicationAnswer(String fieldId, String label, String value) {
			this(fieldId, label, value, 0);
		}
	}
}