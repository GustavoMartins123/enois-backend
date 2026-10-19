package com.socialcoisa.rede.moderation.domain;

import java.time.Instant;

/** Voto de um moderador numa proposta de punicao — um por proposta, alteravel ate a decisao. */
public record SanctionProposalVote(String id, String proposalId, String moderatorId, SanctionVoteType vote,
		String note, Instant createdAt) {

	public static SanctionProposalVote create(String id, String proposalId, String moderatorId,
			SanctionVoteType vote, String note, Instant now) {
		return new SanctionProposalVote(id, proposalId, moderatorId, vote, note, now);
	}

	public SanctionProposalVote changeTo(SanctionVoteType newVote, String newNote) {
		return new SanctionProposalVote(id, proposalId, moderatorId, newVote, newNote, createdAt);
	}
}
