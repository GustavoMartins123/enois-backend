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
 * <p>Login: after 5 failures in 1 minute for the same account AND IP, each attempt is delayed
 * progressively (no lock, never a 429; o acerto zera o contador). Failures against the account from
 * any network add a second, smaller capped delay once they pass 4x that limit, so spraying one
 * account from many IPs still slows down without letting a third party lock the owner out.
 * Signup: 10 por hora por IP. Os tetos são configuráveis — o perfil dev/testes abre
 * (a suíte cria centenas de contas do mesmo IP).</p>
 */
@Component
public class AuthThrottle {

	private static final Duration LOGIN_WINDOW = Duration.ofMinutes(1);
	private static final Duration SIGNUP_WINDOW = Duration.ofHours(1);
	/** Teto do mapa: evita crescer sem fim sob ataque distribuído (as chaves velhas expiram). */
	private static final int MAX_KEYS = 50_000;
	/** Per-account (any network) failures needed before the account-wide delay starts, as a multiple. */
	private static final int ACCOUNT_FAILURE_FACTOR = 4;
	private static final long ACCOUNT_IP_MAX_DELAY_MILLIS = 2_000L;
	/** Small on purpose: anyone can feed this counter, so it may slow the owner a little, never lock. */
	private static final long ACCOUNT_MAX_DELAY_MILLIS = 1_000L;

	private final int loginMaxFalhas;
	private final int signupMaxPorJanela;

	private final Map<String, Janela> falhasLogin = new ConcurrentHashMap<>();
	private final Map<String, Janela> falhasPorConta = new ConcurrentHashMap<>();
	private final Map<String, Janela> signupsPorIp = new ConcurrentHashMap<>();

	public AuthThrottle(
			@Value("${rede.auth.throttle.login-max-failures:5}") int loginMaxFalhas,
			@Value("${rede.auth.throttle.signup-per-hour:10}") int signupMaxPorJanela) {
		this.loginMaxFalhas = loginMaxFalhas;
		this.signupMaxPorJanela = signupMaxPorJanela;
	}

	/**
	 * Atraso progressivo, nunca bloqueio: o lockout por conta deixava qualquer pessoa derrubar a
	 * vítima com 5 senhas erradas, inclusive impedindo a senha CORRETA. Keyed by account + IP, so
	 * failures from another network never slow the owner down; os limites por IP seguem.
	 */
	public void exigeLoginPermitido(String email, String ip) {
		long millis = loginDelayMillis(email, ip);
		if (millis <= 0) {
			return;
		}
		try {
			Thread.sleep(millis);
		}
		catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
		}
	}

	/** The larger of the account+IP delay and the capped account-wide delay; 0 when neither applies. */
	long loginDelayMillis(String email, String ip) {
		int porRede = falhasRecentes(falhasLogin, loginKey(email, ip));
		long delay = porRede >= loginMaxFalhas ? Math.min(ACCOUNT_IP_MAX_DELAY_MILLIS, 200L * porRede) : 0;
		int porConta = falhasRecentes(falhasPorConta, normaliza(email));
		if (porConta >= loginMaxFalhas * ACCOUNT_FAILURE_FACTOR) {
			delay = Math.max(delay, Math.min(ACCOUNT_MAX_DELAY_MILLIS, 20L * porConta));
		}
		return delay;
	}

	private int falhasRecentes(Map<String, Janela> mapa, String chave) {
		Janela janela = mapa.get(chave);
		if (janela == null) {
			return 0;
		}
		janela.podado(Instant.now(), LOGIN_WINDOW);
		return janela.contagem();
	}

	/** Falha de credenciais: conta a janela (o êxito limpa). */
	public void registraFalhaLogin(String email, String ip) {
		registraFalha(falhasLogin, loginKey(email, ip));
		registraFalha(falhasPorConta, normaliza(email));
	}

	private void registraFalha(Map<String, Janela> mapa, String chave) {
		if (mapa.size() > MAX_KEYS) {
			mapa.clear();
		}
		mapa.compute(chave, (k, existente) -> {
			Instant agora = Instant.now();
			if (existente == null || Duration.between(existente.ultimo(), agora).compareTo(LOGIN_WINDOW) > 0) {
				return new Janela(agora);
			}
			existente.registra(agora);
			return existente;
		});
	}

	/** Login com sucesso: os contadores da conta zeram. */
	public void limpaLogin(String email, String ip) {
		falhasLogin.remove(loginKey(email, ip));
		falhasPorConta.remove(normaliza(email));
	}

	private String loginKey(String email, String ip) {
		return normaliza(email) + "|" + normaliza(ip);
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
