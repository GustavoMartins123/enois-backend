package com.socialcoisa.rede.moderation.domain;

import java.time.Instant;

/**
 * Proposta de punicao feita por um moderador. So e aplicada quando mais da metade dos moderadores
 * ativos concorda; antes de {@code MIN_MODERATORS_FOR_SANCTION} moderadores o fluxo nem abre e a
 * decisao continua exclusiva dos administradores.
 */
public record SanctionProposal(String id, String targetUserId, String targetUsername, SanctionType type,
		String reason, String proposerId, SanctionProposalStatus status, int requiredVotes, Instant createdAt,
		Instant decidedAt) {

	public static SanctionProposal create(String id, String targetUserId, String targetUsername, SanctionType type,
			String reason, String proposerId, int requiredVotes, Instant now) {
		return new SanctionProposal(id, targetUserId, targetUsername, type, reason, proposerId,
			SanctionProposalStatus.PENDING, requiredVotes, now, null);
	}

	public SanctionProposal apply(Instant at) {
		return new SanctionProposal(id, targetUserId, targetUsername, type, reason, proposerId,
			SanctionProposalStatus.APPLIED, requiredVotes, createdAt, at);
	}

	public SanctionProposal reject(Instant at) {
		return new SanctionProposal(id, targetUserId, targetUsername, type, reason, proposerId,
			SanctionProposalStatus.REJECTED, requiredVotes, createdAt, at);
	}

	public SanctionProposal cancel(Instant at) {
		return new SanctionProposal(id, targetUserId, targetUsername, type, reason, proposerId,
			SanctionProposalStatus.CANCELLED, requiredVotes, createdAt, at);
	}

	public SanctionProposal withRequiredVotes(int required) {
		return new SanctionProposal(id, targetUserId, targetUsername, type, reason, proposerId, status,
			required, createdAt, decidedAt);
	}
}
