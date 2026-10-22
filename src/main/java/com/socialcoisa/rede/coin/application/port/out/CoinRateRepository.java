package com.socialcoisa.rede.coin.application.port.out;

import java.util.List;
import java.util.Optional;

import com.socialcoisa.rede.coin.domain.CoinRate;

public interface CoinRateRepository {

	CoinRate save(CoinRate rate);

	Optional<CoinRate> findLatest();

	/** Histórico mais recente primeiro (limitado). */
	List<CoinRate> history(int limit);
}
