package com.socialcoisa.rede.coin.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.socialcoisa.rede.coin.application.port.out.CoinRateRepository;
import com.socialcoisa.rede.coin.domain.CoinRate;
import com.socialcoisa.rede.shared.web.ApiException;

/**
 * A cotação de REFERÊNCIA da faísca (plano C7/Fase 5): um preço fixo e publicado em BRL
 * que financia o saque de criador pelo revenue do marketplace. A moderação/administração
 * muda; o histórico alimenta a mini bolsa do app. NÃO é câmbio — não compra faísca.
 */
@Service
public class CoinRateService {

	private final CoinRateRepository rates;
	private final Clock clock;

	public CoinRateService(CoinRateRepository rates, Clock clock) {
		this.rates = rates;
		this.clock = clock;
	}

	/** A cotação vigente: a última publicada; na primeira vez, semeia a inicial (R$ 0,01). */
	@Transactional
	public long currentBrlCents() {
		return latest().brlCents();
	}

	@Transactional(readOnly = true)
	public CoinRate latest() {
		return rates.findLatest().orElseGet(this::seedInitial);
	}

	@Transactional(readOnly = true)
	public List<CoinRate> history(int limit) {
		return rates.history(Math.min(Math.max(limit, 2), 365));
	}

	/** Publica uma nova cotação de referência (a linha nova vira a vigente). */
	@Transactional
	public CoinRate publish(long brlCents, String note) {
		CoinRate rate = CoinRate.create(brlCents, note, Instant.now(clock));
		return rates.save(rate);
	}

	private CoinRate seedInitial() {
		return rates.save(CoinRate.create(CoinRate.INITIAL_BRL_CENTS, "Cotação inicial de referência",
			Instant.now(clock)));
	}
}
