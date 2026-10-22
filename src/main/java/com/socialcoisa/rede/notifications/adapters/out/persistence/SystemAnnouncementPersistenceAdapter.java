package com.socialcoisa.rede.notifications.adapters.out.persistence;

import java.time.Instant;

import org.springframework.stereotype.Repository;

import com.socialcoisa.rede.notifications.application.port.out.SystemAnnouncementRepository;

@Repository
class SystemAnnouncementPersistenceAdapter implements SystemAnnouncementRepository {

	private final SpringDataSystemAnnouncementJpaRepository repository;

	SystemAnnouncementPersistenceAdapter(SpringDataSystemAnnouncementJpaRepository repository) {
		this.repository = repository;
	}

	@Override
	public boolean isSent(String key) {
		return repository.existsById(key);
	}

	@Override
	public void markSent(String key, Instant at) {
		repository.save(new SystemAnnouncementJpaEntity(key, at));
	}
}
