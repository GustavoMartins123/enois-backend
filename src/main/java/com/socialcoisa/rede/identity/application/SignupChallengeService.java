package com.socialcoisa.rede.identity.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.socialcoisa.rede.shared.security.Hashes;
import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Captcha próprio para o cadastro quando o Cloudflare Turnstile não está configurado.
 *
 * É um desafio de soma assinado (HMAC) e com prazo curto: o servidor não guarda estado, então
 * funciona com qualquer número de pods. O token leva o hash da resposta, nunca a resposta; o
 * cliente recebe só a pergunta. Além da resposta certa, exigimos pelo menos 1,2s desde a emissão
 * (um script que resolve na hora não passa) e não aceitamos o mesmo token duas vezes neste pod.
 *
 * O honeypot e o rate limit por IP continuam valendo em paralelo. Quando TURNSTILE_SITE_KEY e
 * TURNSTILE_SECRET_KEY existirem, o Turnstile assume e este desafio não é usado.
 */
@Service
public class SignupChallengeService {

	private static final Logger LOG = LoggerFactory.getLogger(SignupChallengeService.class);
	private static final Duration TTL = Duration.ofMinutes(5);
	private static final long MIN_ELAPSED_MS = 1_200L;
	private static final int MAX_USED = 20_000;

	private final boolean enabled;
	private final Clock clock;
	private final SecureRandom random = new SecureRandom();
	private final byte[] secret;
	private final Map<String, Long> used = new ConcurrentHashMap<>();

	public SignupChallengeService(Clock clock,
			@Value("${rede.security.signup-captcha-enabled:true}") boolean enabled,
			@Value("${rede.security.encryption-key:}") String encryptionKey) {
		this.enabled = enabled;
		this.clock = clock;
		this.secret = resolveSecret(encryptionKey);
	}

	public boolean isEnabled() {
		return enabled;
	}

	public Challenge issue() {
		int left = 2 + random.nextInt(8);
		int right = 2 + random.nextInt(8);
		int answer = left + right;
		Instant now = clock.instant();
		Instant expiresAt = now.plus(TTL);
		String nonce = UUID.randomUUID().toString().replace("-", "");
		String payload = now.toEpochMilli() + "|" + expiresAt.toEpochMilli() + "|"
			+ Hashes.sha256Hex(Integer.toString(answer)) + "|" + nonce;
		String token = encode(payload.getBytes(StandardCharsets.UTF_8)) + "." + encode(sign(payload));
		return new Challenge(token, "Quanto é " + left + " + " + right + "?", expiresAt);
	}

	public void require(String challengeId, Integer answer) {
		if (!enabled) {
			return;
		}
		if (!StringUtils.hasText(challengeId) || answer == null) {
			throw new ApiException(400, "SIGNUP_CAPTCHA_REQUIRED",
				"Resolva a verificação de segurança para criar a conta.");
		}
		String[] parts = challengeId.split("\\.");
		if (parts.length != 2) {
			throw invalid();
		}
		byte[] payloadBytes;
		byte[] signature;
		try {
			payloadBytes = Base64.getUrlDecoder().decode(parts[0]);
			signature = Base64.getUrlDecoder().decode(parts[1]);
		}
		catch (IllegalArgumentException exception) {
			throw invalid();
		}
		if (!MessageDigest.isEqual(signature, sign(new String(payloadBytes, StandardCharsets.UTF_8)))) {
			throw invalid();
		}

		String[] fields = new String(payloadBytes, StandardCharsets.UTF_8).split("\\|");
		if (fields.length != 4) {
			throw invalid();
		}
		long issuedAt;
		long expiresAt;
		try {
			issuedAt = Long.parseLong(fields[0]);
			expiresAt = Long.parseLong(fields[1]);
		}
		catch (NumberFormatException exception) {
			throw invalid();
		}
		long now = clock.millis();
		if (expiresAt < now) {
			throw new ApiException(400, "SIGNUP_CAPTCHA_EXPIRED",
				"A verificação de segurança expirou. Recarregue e tente de novo.");
		}
		if (now - issuedAt < MIN_ELAPSED_MS) {
			throw new ApiException(400, "SIGNUP_CAPTCHA_TOO_FAST",
				"Calma aí: resolva a verificação com um tempinho e envie de novo.");
		}
		if (!MessageDigest.isEqual(fields[2].getBytes(StandardCharsets.UTF_8),
				Hashes.sha256Hex(Integer.toString(answer)).getBytes(StandardCharsets.UTF_8))) {
			throw invalid();
		}
		remember(fields[3], expiresAt, now);
	}

	private void remember(String nonce, long expiresAt, long now) {
		if (used.size() > MAX_USED) {
			used.entrySet().removeIf(entry -> entry.getValue() < now);
		}
		Long previous = used.putIfAbsent(nonce, expiresAt);
		if (previous != null) {
			throw new ApiException(400, "SIGNUP_CAPTCHA_REUSED",
				"Esta verificação já foi usada. Recarregue e tente de novo.");
		}
	}

	private ApiException invalid() {
		return new ApiException(400, "SIGNUP_CAPTCHA_INVALID",
			"Verificação de segurança inválida. Recarregue e tente de novo.");
	}

	private byte[] sign(String payload) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret, "HmacSHA256"));
			return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
		}
		catch (Exception exception) {
			throw new IllegalStateException("HmacSHA256 indisponível", exception);
		}
	}

	private static String encode(byte[] value) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
	}

	private static byte[] resolveSecret(String encryptionKey) {
		if (StringUtils.hasText(encryptionKey)) {
			try {
				byte[] decoded = Base64.getDecoder().decode(encryptionKey.trim());
				if (decoded.length >= 16) {
					return decoded;
				}
			}
			catch (IllegalArgumentException ignored) {
				// Chave fora do formato base64: cai na chave efêmera abaixo.
			}
		}
		byte[] generated = new byte[32];
		new SecureRandom().nextBytes(generated);
		LOG.warn("Chave de criptografia ausente: o desafio de cadastro usa uma chave efêmera por pod.");
		return generated;
	}

	public record Challenge(String challengeId, String question, Instant expiresAt) {
	}
}
