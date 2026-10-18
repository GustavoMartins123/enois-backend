package com.socialcoisa.rede.coin.adapters.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.socialcoisa.rede.coin.application.port.out.CoinDisputeRepository;
import com.socialcoisa.rede.coin.domain.CoinDispute;

interface SpringDataCoinDisputeRepository
		extends org.springframework.data.repository.Repository<CoinDisputeJpaEntity, String> {

	CoinDisputeJpaEntity save(CoinDisputeJpaEntity entity);

	Optional<CoinDisputeJpaEntity> findById(String id);

	boolean existsByEntryRef(String entryRef);

	List<CoinDisputeJpaEntity> findByStateOrderByCreatedAtAsc(String state, Pageable pageable);

	List<CoinDisputeJpaEntity> findBySenderIdOrderByCreatedAtDesc(String senderId);
}

@Repository
@Transactional(propagation = Propagation.REQUIRED)
class CoinDisputePersistenceAdapter implements CoinDisputeRepository {

	private final SpringDataCoinDisputeRepository disputes;

	CoinDisputePersistenceAdapter(SpringDataCoinDisputeRepository disputes) {
		this.disputes = disputes;
	}

	@Override
	public CoinDispute save(CoinDispute dispute) {
		return disputes.save(CoinDisputeJpaEntity.from(dispute)).toDomain();
	}

	@Override
	public Optional<CoinDispute> findById(String id) {
		return disputes.findById(id).map(CoinDisputeJpaEntity::toDomain);
	}

	@Override
	public boolean existsByEntryRef(String entryRef) {
		return disputes.existsByEntryRef(entryRef);
	}

	@Override
	public List<CoinDispute> findPending(int limit) {
		return disputes.findByStateOrderByCreatedAtAsc(CoinDispute.State.OPEN.name(), Pageable.ofSize(Math.min(limit, 200)))
			.stream().map(CoinDisputeJpaEntity::toDomain).toList();
	}

	@Override
	public List<CoinDispute> findBySender(String senderId) {
		return disputes.findBySenderIdOrderByCreatedAtDesc(senderId).stream()
			.map(CoinDisputeJpaEntity::toDomain).toList();
	}
}
