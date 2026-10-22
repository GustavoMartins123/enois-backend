package com.socialcoisa.rede.shared.security;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Limite de frequência por AÇÃO e por USUÁRIO (janela deslizante em memória) — o mesmo padrão
 * do AuthThrottle, aplicado às ações que alimentam a credibilidade (denúncias, recomendações).
 *
 * <p>Proporcional ao porte da comunidade: não é fingerprinting nem bloqueio por IP; é um teto
 * de quantas vezes a MESMA conta pode repetir uma ação numa janela. O mapa é podado quando
 * cresce demais (chaves velhas expiram sozinhas).</p>
 */
@Component
public class ActionThrottle {

	private static final int MAX_KEYS = 50_000;

	private final Map<String, Janela> janelas = new ConcurrentHashMap<>();

	/**
	 * Consome uma tentativa. Lança 429 ACTION_THROTTLED quando o teto da janela foi atingido.
	 *
	 * @param acao    identificador curto (ex.: "report", "recommendation")
	 * @param usuario id de quem age
	 * @param maximo  quantas vezes a ação é permitida na janela
	 * @param janela  tamanho da janela deslizante
	 */
	public void exigePermitido(String acao, String usuario, int maximo, Duration janela) {
		String chave = acao + ":" + (usuario == null ? "anon" : usuario);
		Instant agora = Instant.now();
		Janela registros = janelas.computeIfAbsent(chave, k -> new Janela());
		synchronized (registros) {
			registros.podar(agora, janela);
			if (registros.marcas.size() >= maximo) {
				throw new ApiException(429, "ACTION_THROTTLED",
					"Muitas ações deste tipo em pouco tempo. Tente de novo mais tarde.");
			}
			registros.marcas.addLast(agora);
		}
		if (janelas.size() > MAX_KEYS) {
			janelas.entrySet().removeIf(entry -> entry.getValue().vazia(agora, janela));
		}
	}

	private static final class Janela {

		private final Deque<Instant> marcas = new ArrayDeque<>();

		private void podar(Instant agora, Duration janela) {
			Instant limite = agora.minus(janela);
			while (!marcas.isEmpty() && marcas.peekFirst().isBefore(limite)) {
				marcas.removeFirst();
			}
		}

		private boolean vazia(Instant agora, Duration janela) {
			synchronized (this) {
				podar(agora, janela);
				return marcas.isEmpty();
			}
		}
	}
}
