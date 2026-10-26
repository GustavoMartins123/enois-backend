package com.socialcoisa.rede.identity.adapters.out;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.socialcoisa.rede.shared.web.ApiException;

import tools.jackson.databind.ObjectMapper;

/**
 * Valida tokens do Cloudflare Turnstile.
 *
 * Fail-closed em produção (E-35): uma falha de rede ou uma resposta ambígua NÃO libera o cadastro/
 * login — o Cloudflare é a fronteira anti-bot. A resposta é lida como JSON de verdade, não por
 * substring. Sem secret configurado (dev/testes), a validação é ignorada de propósito.
 */
@Component
public class TurnstileValidator {

	private static final Logger LOG = LoggerFactory.getLogger(TurnstileValidator.class);
	private static final String VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

	private final String secretKey;
	private final String siteKey;
	private final boolean rejectTestKeys;
	private final ObjectMapper objectMapper;
	private final HttpClient httpClient;

	public TurnstileValidator(@Value("${rede.turnstile.secret-key:}") String secretKey,
			@Value("${rede.turnstile.site-key:}") String siteKey,
			@Value("${rede.turnstile.reject-test-keys:true}") boolean rejectTestKeys,
			ObjectMapper objectMapper) {
		this.secretKey = secretKey;
		this.siteKey = siteKey;
		this.rejectTestKeys = rejectTestKeys;
		this.objectMapper = objectMapper;
		this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
		// Cloudflare's documented test keys accept any token: outside dev the app refuses to boot
		// with them instead of running without a real captcha.
		if (rejectTestKeys && (isTestKey(secretKey) || isTestKey(siteKey))) {
			throw new IllegalStateException("TURNSTILE_SITE_KEY/TURNSTILE_SECRET_KEY are Cloudflare test keys. "
				+ "Configure real keys or leave both empty (the signup challenge is used instead).");
		}
		if (isTestKey(secretKey) || isTestKey(siteKey)) {
			// A guarda de boot esta desligada de proposito neste ambiente (QA). O log alto e o que
			// substitui a recusa: ninguem deve confundir "subiu" com "captcha externo ativo".
			LOG.error("TURNSTILE_* sao chaves de TESTE da Cloudflare: o captcha externo esta DESLIGADO neste "
					+ "ambiente e o cadastro usa o desafio proprio do backend. Em producao a aplicacao recusa subir.");
		}
	}

	/**
	 * A proteção externa está configurada? Sem isso, o cadastro usa o desafio próprio.
	 *
	 * Chave de teste nunca conta como configurada, com ou sem a guarda de boot: ela aceita qualquer
	 * token no siteverify, então tratá-la como "captcha ativo" abriria o cadastro para bot.
	 */
	public boolean isConfigured() {
		return secretKey != null && !secretKey.isBlank() && !isTestKey(secretKey);
	}

	private static boolean isTestKey(String key) {
		if (key == null) {
			return false;
		}
		String value = key.trim().toLowerCase(java.util.Locale.ROOT);
		return value.startsWith("1x000") || value.startsWith("2x000") || value.startsWith("3x000");
	}

	public String siteKey() {
		return siteKey;
	}

	/** Configurado E com site key real: é o que o front consegue renderizar. */
	public boolean isAvailable() {
		return isConfigured() && siteKey != null && !siteKey.isBlank() && !isTestKey(siteKey);
	}

	public void require(String token) {
		if (!isConfigured()) {
			return;
		}
		if (token == null || token.isBlank()) {
			throw new ApiException(403, "TURNSTILE_REQUIRED", "Verificacao anti-bot obrigatoria.");
		}
		try {
			// Encoded: a token containing '&' or '=' must not add form fields to the verification call.
			String body = "secret=" + java.net.URLEncoder.encode(secretKey, java.nio.charset.StandardCharsets.UTF_8)
				+ "&response=" + java.net.URLEncoder.encode(token, java.nio.charset.StandardCharsets.UTF_8);
			HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(VERIFY_URL))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.timeout(Duration.ofSeconds(5))
				.build();
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			boolean success = objectMapper.readTree(response.body()).path("success").asBoolean(false);
			if (!success) {
				LOG.warn("Turnstile rejeitou o token: {}", response.body());
				throw new ApiException(403, "TURNSTILE_FAILED", "Verificacao anti-bot falhou. Tente novamente.");
			}
		}
		catch (ApiException rejected) {
			throw rejected;
		}
		catch (Exception failure) {
			LOG.error("Erro ao validar Turnstile: {}", failure.getMessage());
			throw new ApiException(503, "TURNSTILE_UNAVAILABLE",
				"Verificacao anti-bot indisponivel agora. Tente novamente em instantes.");
		}
	}
}
