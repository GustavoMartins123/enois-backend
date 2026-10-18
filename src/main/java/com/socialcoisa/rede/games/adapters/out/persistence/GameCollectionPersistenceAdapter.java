package com.socialcoisa.rede.games.adapters.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.socialcoisa.rede.games.application.port.out.GameCollectionRepository;
import com.socialcoisa.rede.games.domain.GameCollection;

interface SpringDataGameCollectionRepository
		extends org.springframework.data.repository.Repository<GameCollectionJpaEntity, String> {

	GameCollectionJpaEntity save(GameCollectionJpaEntity entity);

	Optional<GameCollectionJpaEntity> findById(String id);

	List<GameCollectionJpaEntity> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

	List<GameCollectionJpaEntity> findByCuratorIdOrderByCreatedAtDesc(String curatorId);
}

@Repository
@Transactional(propagation = Propagation.REQUIRED)
class GameCollectionPersistenceAdapter implements GameCollectionRepository {

	private final SpringDataGameCollectionRepository collections;

	GameCollectionPersistenceAdapter(SpringDataGameCollectionRepository collections) {
		this.collections = collections;
	}

	@Override
	public GameCollection save(GameCollection collection) {
		return collections.save(GameCollectionJpaEntity.from(collection)).toDomain();
	}

	@Override
	public Optional<GameCollection> findById(String id) {
		return collections.findById(id).map(GameCollectionJpaEntity::toDomain);
	}

	@Override
	public List<GameCollection> findPublished(int limit) {
		return collections.findByStatusOrderByCreatedAtDesc(GameCollection.Status.PUBLISHED.name(),
			Pageable.ofSize(Math.min(limit, 60))).stream().map(GameCollectionJpaEntity::toDomain).toList();
	}

	@Override
	public List<GameCollection> findByCurator(String curatorId) {
		return collections.findByCuratorIdOrderByCreatedAtDesc(curatorId).stream()
			.map(GameCollectionJpaEntity::toDomain).toList();
	}
}
