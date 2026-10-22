package com.socialcoisa.rede.coin.application.port.out;

import java.util.List;
import java.util.Optional;

import com.socialcoisa.rede.coin.domain.CoinDispute;

public interface CoinDisputeRepository {

	CoinDispute save(CoinDispute dispute);

	Optional<CoinDispute> findById(String id);

	boolean existsByEntryRef(String entryRef);

	/** Pendentes, mais antigas primeiro (a fila da moderação). */
	List<CoinDispute> findPending(int limit);

	List<CoinDispute> findBySender(String senderId);
}
