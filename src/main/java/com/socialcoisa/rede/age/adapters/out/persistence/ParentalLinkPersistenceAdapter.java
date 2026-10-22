package com.socialcoisa.rede.age.adapters.out.persistence;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.socialcoisa.rede.age.application.port.out.ParentalLinkRepository;
import com.socialcoisa.rede.age.domain.ParentalLink;

@Repository
class ParentalLinkPersistenceAdapter implements ParentalLinkRepository {

	private final SpringDataParentalLinkJpaRepository repository;
	private final Clock clock;

	ParentalLinkPersistenceAdapter(SpringDataParentalLinkJpaRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	@Override
	public void save(ParentalLink link) {
		repository.save(ParentalLinkJpaEntity.fromDomain(link));
	}

	@Override
	public Optional<ParentalLink> findByTokenHash(String tokenHash) {
		return repository.findByTokenHash(tokenHash).map(ParentalLinkJpaEntity::toDomain);
	}

	@Override
	public Optional<ParentalLink> findActiveByMinor(String minorUserId) {
		return repository.findFirstByMinorUserIdAndStatus(minorUserId, ParentalLink.Status.ACTIVE)
			.map(ParentalLinkJpaEntity::toDomain);
	}

	@Override
	public List<ParentalLink> findActiveByGuardian(String guardianUserId) {
		return repository.findByGuardianUserIdAndStatus(guardianUserId, ParentalLink.Status.ACTIVE).stream()
			.map(ParentalLinkJpaEntity::toDomain)
			.toList();
	}

	@Override
	public void cancelPendingByMinor(String minorUserId) {
		Instant now = clock.instant();
		repository.cancelPendingByMinor(minorUserId, ParentalLink.Status.PENDING, ParentalLink.Status.REVOKED, now);
	}
}
