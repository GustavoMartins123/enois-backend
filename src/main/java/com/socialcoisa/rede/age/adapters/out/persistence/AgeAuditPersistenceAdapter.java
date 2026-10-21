package com.socialcoisa.rede.age.adapters.out.persistence;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.socialcoisa.rede.age.application.port.out.AgeAuditRepository;

@Repository
class AgeAuditPersistenceAdapter implements AgeAuditRepository {

	private final SpringDataAgeAuditJpaRepository repository;
	private final Clock clock;

	AgeAuditPersistenceAdapter(SpringDataAgeAuditJpaRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	@Override
	public void record(String userId, String event, String actorId, String detail) {
		String id = "age_" + UUID.randomUUID().toString().replace("-", "");
		repository.save(AgeAuditJpaEntity.create(id, userId, event, actorId, truncate(detail), clock.instant()));
	}

	private String truncate(String detail) {
		if (detail == null) {
			return null;
		}
		return detail.length() > 500 ? detail.substring(0, 500) : detail;
	}
}
