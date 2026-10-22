package com.socialcoisa.rede.moderation.adapters.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.socialcoisa.rede.moderation.application.port.out.ModerationVoteRepository;
import com.socialcoisa.rede.moderation.domain.ModerationVote;

@Repository
class ModerationVotePersistenceAdapter implements ModerationVoteRepository {

	private final SpringDataModerationVoteJpaRepository jpaRepository;

	ModerationVotePersistenceAdapter(SpringDataModerationVoteJpaRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	public ModerationVote save(ModerationVote vote) {
		return jpaRepository.save(ModerationVoteJpaEntity.fromDomain(vote)).toDomain();
	}

	@Override
	public Optional<ModerationVote> findByReportAndModerator(String reportId, String moderatorId) {
		return jpaRepository.findByReportIdAndModeratorId(reportId, moderatorId)
			.map(ModerationVoteJpaEntity::toDomain);
	}

	@Override
	public List<ModerationVote> findByReport(String reportId) {
		return jpaRepository.findByReportId(reportId).stream()
			.map(ModerationVoteJpaEntity::toDomain)
			.toList();
	}
}
