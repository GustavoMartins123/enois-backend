package com.socialcoisa.rede.moderation.domain;

import java.time.Instant;

public class Report {

	private final String id;
	private final String reporterId;
	private final TargetType targetType;
	private final String targetId;
	private final ReportReason reason;
	private final String description;
	private final Instant createdAt;
	private ReportStatus status;
	private String reviewedBy;
	private Instant reviewedAt;
	private String resolutionNote;
	/** Palpite estruturado do agente de IA (JSON validado). Nao e prova nem acao. */
	private String aiTriage;
	private Instant aiTriagedAt;

	private Report(String id, String reporterId, TargetType targetType, String targetId, ReportReason reason,
			String description, ReportStatus status, Instant createdAt, String reviewedBy, Instant reviewedAt,
			String resolutionNote, String aiTriage, Instant aiTriagedAt) {
		this.id = id;
		this.reporterId = reporterId;
		this.targetType = targetType;
		this.targetId = targetId;
		this.reason = reason;
		this.description = description;
		this.status = status;
		this.createdAt = createdAt;
		this.reviewedBy = reviewedBy;
		this.reviewedAt = reviewedAt;
		this.resolutionNote = resolutionNote;
		this.aiTriage = aiTriage;
		this.aiTriagedAt = aiTriagedAt;
	}

	public static Report create(String id, String reporterId, TargetType targetType, String targetId,
			ReportReason reason, String description, Instant createdAt) {
		return new Report(id, reporterId, targetType, targetId, reason, description, ReportStatus.PENDING, createdAt,
			null, null, null, null, null);
	}

	public static Report restore(String id, String reporterId, TargetType targetType, String targetId,
			ReportReason reason, String description, ReportStatus status, Instant createdAt, String reviewedBy,
			Instant reviewedAt, String resolutionNote, String aiTriage, Instant aiTriagedAt) {
		return new Report(id, reporterId, targetType, targetId, reason, description, status, createdAt, reviewedBy,
			reviewedAt, resolutionNote, aiTriage, aiTriagedAt);
	}

	/** Guarda o palpite estruturado do agente de IA (JSON ja validado pelo backend). */
	public void attachAiTriage(String triageJson, Instant at) {
		this.aiTriage = triageJson;
		this.aiTriagedAt = at;
	}

	public void approve(String reviewerId, String note) {
		this.status = ReportStatus.APPROVED;
		this.reviewedBy = reviewerId;
		this.reviewedAt = Instant.now();
		this.resolutionNote = note;
	}

	public void dismiss(String reviewerId, String note) {
		this.status = ReportStatus.DISMISSED;
		this.reviewedBy = reviewerId;
		this.reviewedAt = Instant.now();
		this.resolutionNote = note;
	}

	/** Pre-triagem da IA definiu penalidade: status proprio; o humano entra pelo recurso. */
	public void autoPenalize(String motive) {
		this.status = ReportStatus.AUTO_PENALIZED;
		this.resolutionNote = motive;
	}

	public String id() { return id; }
	public String reporterId() { return reporterId; }
	public TargetType targetType() { return targetType; }
	public String targetId() { return targetId; }
	public ReportReason reason() { return reason; }
	public String description() { return description; }
	public ReportStatus status() { return status; }
	public Instant createdAt() { return createdAt; }
	public String reviewedBy() { return reviewedBy; }
	public Instant reviewedAt() { return reviewedAt; }
	public String resolutionNote() { return resolutionNote; }
	public String aiTriage() { return aiTriage; }
	public Instant aiTriagedAt() { return aiTriagedAt; }
}
