package com.socialcoisa.rede.jobs.adapters.out.persistence;

import com.socialcoisa.rede.jobs.domain.JobApplication;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Resposta de um campo, guardada com o rotulo do momento do envio: se o autor editar a vaga depois,
 * o historico da candidatura continua contando o que foi perguntado na epoca.
 */
@Embeddable
class JobApplicationAnswerEmbeddable {

	@Column(name = "field_id", nullable = false)
	private String fieldId;

	@Column(nullable = false)
	private String label;

	@Column(name = "answer_value", nullable = false, length = 2000)
	private String value;

	@Column(nullable = false)
	private int position;

	protected JobApplicationAnswerEmbeddable() {
	}

	static JobApplicationAnswerEmbeddable fromDomain(JobApplication.JobApplicationAnswer answer) {
		JobApplicationAnswerEmbeddable embeddable = new JobApplicationAnswerEmbeddable();
		embeddable.fieldId = answer.fieldId();
		embeddable.label = answer.label();
		embeddable.value = answer.value();
		embeddable.position = answer.position();
		return embeddable;
	}

	JobApplication.JobApplicationAnswer toDomain() {
		return new JobApplication.JobApplicationAnswer(fieldId, label, value, position);
	}
}
