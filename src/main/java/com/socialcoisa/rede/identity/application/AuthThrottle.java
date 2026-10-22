package com.socialcoisa.rede.identity.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Throttle de credenciais (F-02/F-04 do pentest): o RateLimiter global ("auth", 20/min por
 * pod) é compartilhado por TODOS os usuários — não segura brute force dirigido a UMA conta
 * nem cadastro em massa de UM IP. Aqui: janelas por CHAVE (conta no login, IP no signup)
 * guardadas em memória com expiração — a aplicação continua stateless.
 *
 * <p>Login: 5 falhas em 1 minuto por conta → 429 por 1 minuto (o acerto zera o contador).
 * Signup: 10 por hora por IP. Os tetos são configuráveis — o perfil dev/testes abre
 * (a suíte cria centenas de contas do mesmo IP).</p>
 */
@Component
public class AuthThrottle {

	private static final Duration LOGIN_WINDOW = Duration.ofMinutes(1);
	private static final Duration LOGIN_LOCK = Duration.ofMinutes(1);
	private static final Duration SIGNUP_WINDOW = Duration.ofHours(1);
	/** Teto do mapa: evita crescer sem fim sob ataque distribuído (as chaves velhas expiram). */
	private static final int MAX_KEYS = 50_000;

	private final int loginMaxFalhas;
	private final int signupMaxPorJanela;

	private final Map<String, Janela> falhasLogin = new ConcurrentHashMap<>();
	private final Map<String, Janela> signupsPorIp = new ConcurrentHashMap<>();

	public AuthThrottle(
			@Value("${rede.auth.throttle.login-max-failures:5}") int loginMaxFalhas,
			@Value("${rede.auth.throttle.signup-per-hour:10}") int signupMaxPorJanela) {
		this.loginMaxFalhas = loginMaxFalhas;
		this.signupMaxPorJanela = signupMaxPorJanela;
	}

	/** O login pode tentar? (a conta não está em lock por falhas recentes) */
	public void exigeLoginPermitido(String email) {
		Janela janela = falhasLogin.get(normaliza(email));
		if (janela == null) {
			return;
		}
		Instant agora = Instant.now();
		janela.podado(agora, LOGIN_WINDOW);
		Instant fimDoLock = janela.ultimo().plus(LOGIN_LOCK);
		if (janela.contagem() >= loginMaxFalhas && agora.isBefore(fimDoLock)) {
			throw new ApiException(429, "LOGIN_THROTTLED",
				"Muitas tentativas para esta conta. Tenta de novo em um minuto.");
		}
	}

	/** Falha de credenciais: conta a janela (o êxito limpa). */
	public void registraFalhaLogin(String email) {
		String chave = normaliza(email);
		if (falhasLogin.size() > MAX_KEYS) {
			falhasLogin.clear();
		}
		falhasLogin.compute(chave, (k, existente) -> {
			Instant agora = Instant.now();
			if (existente == null || Duration.between(existente.ultimo(), agora).compareTo(LOGIN_WINDOW) > 0) {
				return new Janela(agora);
			}
			existente.registra(agora);
			return existente;
		});
	}

	/** Login com sucesso: o contador da conta zera. */
	public void limpaLogin(String email) {
		falhasLogin.remove(normaliza(email));
	}

	/**
	 * O IP pode criar conta? (10 por hora por IP).
	 * Quando um fingerprint de dispositivo esta disponivel, tambem limita por fingerprint —
	 * detecta o mesmo navegador criando contas atras de proxies diferentes.
	 */
	public void exigeSignupPermitido(String ip) {
		exigeSignupPermitido(ip, null);
	}

	public void exigeSignupPermitido(String ip, String deviceFingerprint) {
		if (deviceFingerprint != null && !deviceFingerprint.isBlank()) {
			verificaJanela("fp:" + normaliza(deviceFingerprint));
		}
		verificaJanela("ip:" + normaliza(ip));
	}

	private void verificaJanela(String chave) {
		Janela janela = signupsPorIp.get(chave);
		Instant agora = Instant.now();
		if (janela != null) {
			janela.podado(agora, SIGNUP_WINDOW);
			if (janela.contagem() >= signupMaxPorJanela) {
				throw new ApiException(429, "SIGNUP_THROTTLED",
					"Muitas contas criadas a partir desta rede. Tenta de novo mais tarde.");
			}
		}
		if (signupsPorIp.size() > MAX_KEYS) {
			signupsPorIp.clear();
		}
		signupsPorIp.compute(chave, (k, existente) -> {
			Instant momento = Instant.now();
			if (existente == null || Duration.between(existente.ultimo(), momento).compareTo(SIGNUP_WINDOW) > 0) {
				return new Janela(momento);
			}
			existente.registra(momento);
			return existente;
		});
	}

	private String normaliza(String valor) {
		return valor == null ? "" : valor.trim().toLowerCase();
	}

	/** Janela deslizante de timestamps (podados a cada leitura — memória bounded). */
	private static final class Janela {

		private final Map<Instant, Boolean> marcas = new ConcurrentHashMap<>();
		private Instant ultimo;

		private Janela(Instant primeiro) {
			registra(primeiro);
		}

		private void registra(Instant agora) {
			ultimo = agora;
			marcas.put(agora, Boolean.TRUE);
		}

		private void podado(Instant agora, Duration janela) {
			marcas.keySet().removeIf(marca -> Duration.between(marca, agora).compareTo(janela) > 0);
		}

		private int contagem() {
			return marcas.size();
		}

		private Instant ultimo() {
			return ultimo;
		}
	}
}
