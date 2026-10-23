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
	private final boolean failOpen;
	private final boolean rejectTestKeys;
	private final ObjectMapper objectMapper;
	private final HttpClient httpClient;

	public TurnstileValidator(@Value("${rede.turnstile.secret-key:}") String secretKey,
			@Value("${rede.turnstile.site-key:}") String siteKey,
			@Value("${rede.turnstile.fail-open:false}") boolean failOpen,
			@Value("${rede.turnstile.reject-test-keys:true}") boolean rejectTestKeys,
			ObjectMapper objectMapper) {
		this.secretKey = secretKey;
		this.siteKey = siteKey;
		this.failOpen = failOpen;
		this.rejectTestKeys = rejectTestKeys;
		this.objectMapper = objectMapper;
		this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
		if (rejectTestKeys && (isTestKey(secretKey) || isTestKey(siteKey))) {
			LOG.warn("Turnstile com chave de TESTE do Cloudflare: o captcha sera tratado como indisponivel "
				+ "para nao aceitar token dummy (N-10).");
		}
	}

	/** A proteção externa está configurada? Sem isso, o cadastro usa o desafio próprio. */
	public boolean isConfigured() {
		return secretKey != null && !secretKey.isBlank()
			&& !(rejectTestKeys && isTestKey(secretKey));
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

	/** Configurado E com site key: é o que o front consegue renderizar. */
	public boolean isAvailable() {
		return isConfigured() && siteKey != null && !siteKey.isBlank()
			&& !(rejectTestKeys && isTestKey(siteKey));
	}

	public void require(String token) {
		if (!isConfigured()) {
			return;
		}
		if (token == null || token.isBlank()) {
			throw new ApiException(403, "TURNSTILE_REQUIRED", "Verificacao anti-bot obrigatoria.");
		}
		try {
			String body = "secret=" + secretKey + "&response=" + token;
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
			if (!failOpen) {
				throw new ApiException(503, "TURNSTILE_UNAVAILABLE",
					"Verificacao anti-bot indisponivel agora. Tente novamente em instantes.");
			}
		}
	}
}
