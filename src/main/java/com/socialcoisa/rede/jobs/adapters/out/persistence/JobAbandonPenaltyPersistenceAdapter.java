package com.socialcoisa.rede.jobs.adapters.out.persistence;

import java.time.Clock;

import org.springframework.stereotype.Component;

import com.socialcoisa.rede.jobs.application.port.out.JobAbandonPenaltyRepository;
import com.socialcoisa.rede.profile.application.port.out.JobAbandonPenaltyReader;

/**
 * Exclusões de vaga sem resposta (item 7 do backlog). Uma linha por vaga — a chave única
 * garante que o mesmo delete registrado por corrida não contage duas vezes.
 */
@Component
class JobAbandonPenaltyPersistenceAdapter implements JobAbandonPenaltyRepository, JobAbandonPenaltyReader {

	private final SpringDataJobAbandonPenaltyJpaRepository repository;
	private final Clock clock;

	JobAbandonPenaltyPersistenceAdapter(SpringDataJobAbandonPenaltyJpaRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	@Override
	public boolean record(String jobId, String userId, int unanswered) {
		if (repository.existsById(jobId)) {
			return false;
		}
		var entity = new JobAbandonPenaltyJpaEntity();
		entity.jobId = jobId;
		entity.userId = userId;
		entity.unanswered = unanswered;
		entity.recordedAt = clock.instant();
		repository.save(entity);
		return true;
	}

	@Override
	public int countByUser(String userId) {
		return repository.countByUserId(userId);
	}
}
