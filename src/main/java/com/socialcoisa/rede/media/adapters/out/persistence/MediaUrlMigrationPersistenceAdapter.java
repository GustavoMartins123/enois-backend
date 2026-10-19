package com.socialcoisa.rede.media.adapters.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.socialcoisa.rede.media.application.port.out.MediaUrlMigrationRepository;

@Repository
class MediaUrlMigrationPersistenceAdapter implements MediaUrlMigrationRepository {

	private final SpringDataMediaUrlMigrationJpaRepository repository;

	MediaUrlMigrationPersistenceAdapter(SpringDataMediaUrlMigrationJpaRepository repository) {
		this.repository = repository;
	}

	@Override
	public Optional<Row> findByHash(String urlHash) {
		return repository.findById(urlHash).map(MediaUrlMigrationPersistenceAdapter::toRow);
	}

	@Override
	@Transactional
	public void ensurePending(String urlHash, String sourceUrl, Instant now) {
		if (repository.existsById(urlHash)) {
			return;
		}
		var entity = new MediaUrlMigrationJpaEntity();
		entity.urlHash = urlHash;
		entity.sourceUrl = sourceUrl.length() > 1000 ? sourceUrl.substring(0, 1000) : sourceUrl;
		entity.status = Status.PENDING.name();
		entity.attempts = 0;
		entity.updatedAt = now;
		try {
			repository.save(entity);
		}
		catch (DataIntegrityViolationException corrida) {
			// Outra réplica criou primeiro: idempotente por desenho.
		}
	}

	@Override
	@Transactional
	public boolean claim(String urlHash, Instant now, Instant staleBefore, int maxAttempts) {
		return repository.claim(urlHash, now, staleBefore, maxAttempts) > 0;
	}

	@Override
	@Transactional
	public void markCopied(String urlHash, String s3Url, Instant now) {
		repository.findById(urlHash).ifPresent(entity -> {
			entity.status = Status.COPIED.name();
			entity.s3Url = s3Url.length() > 1000 ? s3Url.substring(0, 1000) : s3Url;
			entity.lastError = null;
			entity.claimedAt = null;
			entity.updatedAt = now;
			repository.save(entity);
		});
	}

	@Override
	@Transactional
	public void markActive(String urlHash, Instant now) {
		repository.findById(urlHash).ifPresent(entity -> {
			entity.status = Status.ACTIVE.name();
			entity.claimedAt = null;
			entity.updatedAt = now;
			repository.save(entity);
		});
	}

	@Override
	@Transactional
	public void markFailed(String urlHash, String error, Instant now) {
		repository.findById(urlHash).ifPresent(entity -> {
			entity.status = Status.FAILED.name();
			entity.lastError = error == null ? null : (error.length() > 500 ? error.substring(0, 500) : error);
			entity.claimedAt = null;
			entity.updatedAt = now;
			repository.save(entity);
		});
	}

	@Override
	public List<Row> findForFlip(int limit) {
		return repository.findForFlip(PageRequest.of(0, Math.max(1, limit))).stream()
			.map(MediaUrlMigrationPersistenceAdapter::toRow)
			.toList();
	}

	private static Row toRow(MediaUrlMigrationJpaEntity entity) {
		return new Row(entity.urlHash, entity.sourceUrl, Status.valueOf(entity.status), entity.s3Url,
			entity.attempts, entity.lastError, entity.claimedAt, entity.updatedAt);
	}
}
