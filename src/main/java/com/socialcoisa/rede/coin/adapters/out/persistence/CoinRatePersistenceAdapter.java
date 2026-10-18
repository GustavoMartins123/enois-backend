package com.socialcoisa.rede.coin.adapters.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.socialcoisa.rede.coin.application.port.out.CoinRateRepository;
import com.socialcoisa.rede.coin.domain.CoinRate;

interface SpringDataCoinRateRepository extends org.springframework.data.repository.Repository<CoinRateHistoryJpaEntity, String> {

	CoinRateHistoryJpaEntity save(CoinRateHistoryJpaEntity entity);

	Optional<CoinRateHistoryJpaEntity> findTopByOrderByEffectiveAtDesc();

	List<CoinRateHistoryJpaEntity> findAllByOrderByEffectiveAtDesc(Pageable pageable);
}

@Repository
@Transactional(propagation = Propagation.REQUIRED)
class CoinRatePersistenceAdapter implements CoinRateRepository {

	private final SpringDataCoinRateRepository rates;

	CoinRatePersistenceAdapter(SpringDataCoinRateRepository rates) {
		this.rates = rates;
	}

	@Override
	public CoinRate save(CoinRate rate) {
		return rates.save(CoinRateHistoryJpaEntity.from(rate)).toDomain();
	}

	@Override
	public Optional<CoinRate> findLatest() {
		return rates.findTopByOrderByEffectiveAtDesc().map(CoinRateHistoryJpaEntity::toDomain);
	}

	@Override
	public List<CoinRate> history(int limit) {
		return rates.findAllByOrderByEffectiveAtDesc(Pageable.ofSize(limit)).stream()
			.map(CoinRateHistoryJpaEntity::toDomain).toList();
	}
}
