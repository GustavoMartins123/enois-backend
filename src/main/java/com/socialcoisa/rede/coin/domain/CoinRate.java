package com.socialcoisa.rede.coin.domain;

import java.time.Instant;
import java.util.UUID;

import org.springframework.util.StringUtils;

import com.socialcoisa.rede.shared.web.ApiException;

/**
 * A cotação de REFERÊNCIA da faísca (plano C7/Fase 5): quantos CENTAVOS de real vale
 * 1 faísca — o preço publicado que financia o saque de criador pelo revenue do
 * marketplace. NÃO é câmbio nem bolsa de ativos: não compra faísca com dinheiro.
 */
public record CoinRate(String id, long brlCents, String note, Instant effectiveAt) {

	/** Cotação inicial de referência: 100 faíscas = R$ 1,00. */
	public static final long INITIAL_BRL_CENTS = 1L;

	public static CoinRate create(long brlCents, String note, Instant now) {
		if (brlCents <= 0) {
			throw new ApiException(400, "INVALID_RATE", "A cotação precisa ser positiva (em centavos).");
		}
		return new CoinRate("rate_" + UUID.randomUUID().toString().replace("-", ""), brlCents,
			StringUtils.hasText(note) ? note.trim() : null, now);
	}

	public static CoinRate restore(String id, long brlCents, String note, Instant effectiveAt) {
		return new CoinRate(id, brlCents, note, effectiveAt);
	}
}
