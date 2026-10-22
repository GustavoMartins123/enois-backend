package com.socialcoisa.rede.jobs.adapters.out.persistence;

import com.socialcoisa.rede.jobs.domain.JobApplicationField;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/** Campo personalizado da vaga como parte da propria linha da vaga (colecao de elementos). */
@Embeddable
class JobApplicationFieldEmbeddable {

	@Column(name = "id", nullable = false)
	private String id;

	@Column(nullable = false)
	private String label;

	@Enumerated(EnumType.STRING)
	@Column(name = "field_type", nullable = false, length = 16)
	private JobApplicationField.FieldType fieldType;

	@Column(nullable = false)
	private boolean required;

	@Column(nullable = false)
	private int position;

	protected JobApplicationFieldEmbeddable() {
	}

	static JobApplicationFieldEmbeddable fromDomain(JobApplicationField field) {
		JobApplicationFieldEmbeddable embeddable = new JobApplicationFieldEmbeddable();
		embeddable.id = field.id();
		embeddable.label = field.label();
		embeddable.fieldType = field.type();
		embeddable.required = field.required();
		embeddable.position = field.position();
		return embeddable;
	}

	JobApplicationField toDomain() {
		return JobApplicationField.create(id, null, label, fieldType, required, position);
	}
}
