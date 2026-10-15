package com.socialcoisa.rede.jobs.domain;

import java.util.Locale;

/**
 * Campo personalizado do formulario de candidatura, definido por quem publica a vaga.
 *
 * Cada campo e obrigatorio ou opcional — e essa marca e o que a candidatura valida no envio.
 */
public class JobApplicationField {

	public enum FieldType {
		/** Resposta curta. */
		TEXT,
		/** Resposta longa. */
		TEXTAREA,
		/** Numero (anos de experiencia, pretensao, ...). */
		NUMBER,
		/** Link (portfolio, LinkedIn, GitHub). */
		LINK;

		public static FieldType from(String value) {
			if (value == null || value.isBlank()) {
				return TEXT;
			}
			try {
				return valueOf(value.trim().toUpperCase(Locale.ROOT));
			}
			catch (IllegalArgumentException exception) {
				throw new com.socialcoisa.rede.shared.web.ApiException(400, "INVALID_FIELD_TYPE",
					"Tipo de campo invalido: " + value + ". Use TEXT, TEXTAREA, NUMBER ou LINK.");
			}
		}
	}

	private final String id;
	private final String jobId;
	private final String label;
	private final FieldType type;
	private final boolean required;
	private final int position;

	private JobApplicationField(String id, String jobId, String label, FieldType type, boolean required, int position) {
		this.id = id;
		this.jobId = jobId;
		this.label = label;
		this.type = type;
		this.required = required;
		this.position = position;
	}

	public static JobApplicationField create(String id, String jobId, String label, FieldType type, boolean required,
			int position) {
		return new JobApplicationField(id, jobId, label, type, required, position);
	}

	public String id() {
		return id;
	}

	public String jobId() {
		return jobId;
	}

	public String label() {
		return label;
	}

	public FieldType type() {
		return type;
	}

	public boolean required() {
		return required;
	}

	public int position() {
		return position;
	}
}