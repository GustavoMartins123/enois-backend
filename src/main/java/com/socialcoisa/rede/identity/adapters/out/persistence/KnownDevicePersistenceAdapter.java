package com.socialcoisa.rede.identity.adapters.out.persistence;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.socialcoisa.rede.identity.application.port.out.KnownDeviceRepository;

@Repository
class KnownDevicePersistenceAdapter implements KnownDeviceRepository {

	private final SpringDataKnownDeviceJpaRepository repository;
	private final Clock clock;

	KnownDevicePersistenceAdapter(SpringDataKnownDeviceJpaRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	@Override
	public boolean exists(String userId, String fingerprint) {
		return repository.existsByUserIdAndFingerprint(userId, fingerprint);
	}

	@Override
	public void save(String userId, String fingerprint) {
		String id = "device_" + UUID.randomUUID().toString().replace("-", "");
		repository.save(new KnownDeviceJpaEntity(id, userId, fingerprint, clock.instant()));
	}
}
