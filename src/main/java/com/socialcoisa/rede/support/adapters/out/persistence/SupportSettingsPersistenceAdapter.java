package com.socialcoisa.rede.support.adapters.out.persistence;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.socialcoisa.rede.support.application.port.out.SupportSettingsRepository;

@Repository
class SupportSettingsPersistenceAdapter implements SupportSettingsRepository {

	private final SpringDataSupportSettingJpaRepository repository;

	SupportSettingsPersistenceAdapter(SpringDataSupportSettingJpaRepository repository) {
		this.repository = repository;
	}

	@Override
	public Optional<String> findCipher(String name) {
		return repository.findById(name).map(SupportSettingJpaEntity::valueCipher);
	}

	@Override
	public void saveCipher(String name, String cipher, Instant now) {
		SupportSettingJpaEntity entity = repository.findById(name)
			.orElseGet(() -> new SupportSettingJpaEntity(name, cipher, now));
		entity.update(cipher, now);
		repository.save(entity);
	}

	@Override
	public void delete(String name) {
		repository.deleteById(name);
	}
}
