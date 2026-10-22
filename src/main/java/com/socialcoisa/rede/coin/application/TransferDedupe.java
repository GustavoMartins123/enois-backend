package com.socialcoisa.rede.coin.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.socialcoisa.rede.coin.domain.CoinEntry;

/**
 * O mesmo Pix repetido em poucos segundos é UM Pix.
 *
 * O duplo clique (e o retry automático depois de um timeout) mandava duas transferências
 * idênticas com chaves de idempotência diferentes — a chave nasce por requisição, então a
 * idempotência por chave não pega esse caso. Aqui a chave é o CONTEÚDO: mesma conta, mesmo
 * destino, mesmo valor e mesmo recado dentro da janela devolvem a entrada da primeira.
 *
 * A janela é curta de propósito (padrão 10s) e vive no pod, como os outros limitadores do
 * projeto: quem quiser mandar o MESMO valor de novo de verdade espera a janela passar.
 */
@Component
public class TransferDedupe {

	private static final int MAX_KEYS = 50_000;

	private final Map<String, Repetida> recentes = new ConcurrentHashMap<>();
	private final Clock clock;
	private final Duration janela;

	public TransferDedupe(Clock clock, @Value("${rede.coin.transfer-dedupe-seconds:10}") long seconds) {
		this.clock = clock;
		this.janela = Duration.ofSeconds(Math.max(0, Math.min(seconds, 300)));
	}

	/** A entrada da transferência idêntica ainda dentro da janela (ou null). */
	public CoinEntry recent(String fromAccountId, String toUserId, long amount, String message) {
		if (janela.isZero()) {
			return null;
		}
		Instant agora = clock.instant();
		Repetida repetida = recentes.get(key(fromAccountId, toUserId, amount, message));
		if (repetida == null) {
			return null;
		}
		if (repetida.enviadaEm().plus(janela).isBefore(agora)) {
			recentes.remove(key(fromAccountId, toUserId, amount, message));
			return null;
		}
		return repetida.entry();
	}

	public void remember(String fromAccountId, String toUserId, long amount, String message, CoinEntry entry) {
		if (janela.isZero()) {
			return;
		}
		Instant agora = clock.instant();
		recentes.put(key(fromAccountId, toUserId, amount, message), new Repetida(entry, agora));
		if (recentes.size() > MAX_KEYS) {
			recentes.entrySet().removeIf(e -> e.getValue().enviadaEm().plus(janela).isBefore(agora));
		}
	}

	private String key(String fromAccountId, String toUserId, long amount, String message) {
		return fromAccountId + "|" + toUserId + "|" + amount + "|" + (message == null ? "" : message.trim());
	}

	private record Repetida(CoinEntry entry, Instant enviadaEm) {
	}
}
