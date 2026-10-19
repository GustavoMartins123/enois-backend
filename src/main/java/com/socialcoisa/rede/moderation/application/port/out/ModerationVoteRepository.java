package com.socialcoisa.rede.moderation.application.port.out;

import java.util.List;
import java.util.Optional;

import com.socialcoisa.rede.moderation.domain.ModerationVote;

public interface ModerationVoteRepository {

	/** Cria ou atualiza o voto do moderador naquela denuncia (unique report_id + moderator_id). */
	ModerationVote save(ModerationVote vote);

	Optional<ModerationVote> findByReportAndModerator(String reportId, String moderatorId);

	List<ModerationVote> findByReport(String reportId);
}
