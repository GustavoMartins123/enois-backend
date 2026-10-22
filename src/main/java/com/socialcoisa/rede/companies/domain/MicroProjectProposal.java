package com.socialcoisa.rede.companies.domain;

import java.time.Instant;

public record MicroProjectProposal(String id, String projectId, String userId, String message,
		ProposalStatus status, Instant proposedAt, Instant decidedAt) {

	public static MicroProjectProposal create(String id, String projectId, String userId, String message,
			Instant now) {
		return new MicroProjectProposal(id, projectId, userId, message, ProposalStatus.SENT, now, null);
	}

	public static MicroProjectProposal restore(String id, String projectId, String userId, String message,
			ProposalStatus status, Instant proposedAt, Instant decidedAt) {
		return new MicroProjectProposal(id, projectId, userId, message, status, proposedAt, decidedAt);
	}

	public MicroProjectProposal select(Instant at) {
		return new MicroProjectProposal(id, projectId, userId, message, ProposalStatus.SELECTED, proposedAt, at);
	}

	public MicroProjectProposal decline(Instant at) {
		return new MicroProjectProposal(id, projectId, userId, message, ProposalStatus.DECLINED, proposedAt, at);
	}

	public boolean isSent() {
		return status == ProposalStatus.SENT;
	}
}
