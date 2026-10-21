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

/**
 * Valida tokens do Cloudflare Turnstile.
 *
 * Quando {@code TURNSTILE_SECRET_KEY} nao esta configurada, a validacao e ignorada — assim o
 * desenvolvimento local e os testes rodam sem depender do servico externo. Em producao, a chave
 * deve ser configurada para ativar a protecao.
 */
@Component
public class TurnstileValidator {

	private static final Logger LOG = LoggerFactory.getLogger(TurnstileValidator.class);
	private static final String VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

	private final String secretKey;
	private final String siteKey;
	private final HttpClient httpClient;

	public TurnstileValidator(@Value("${rede.turnstile.secret-key:}") String secretKey,
			@Value("${rede.turnstile.site-key:}") String siteKey) {
		this.secretKey = secretKey;
		this.siteKey = siteKey;
		this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	}

	/** A proteção externa está configurada? Sem isso, o cadastro usa o desafio próprio. */
	public boolean isConfigured() {
		return secretKey != null && !secretKey.isBlank();
	}

	public String siteKey() {
		return siteKey;
	}

	/** Configurado E com site key: é o que o front consegue renderizar. */
	public boolean isAvailable() {
		return isConfigured() && siteKey != null && !siteKey.isBlank();
	}

	/**
	 * Valida o token Turnstile. Lanca 403 se o token for invalido.
	 * Se a secret key nao estiver configurada, a validacao e ignorada (modo dev).
	 */
	public void require(String token) {
		if (secretKey == null || secretKey.isBlank()) {
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
			if (!response.body().contains("\"success\":true")) {
				LOG.warn("Turnstile rejeitou o token: {}", response.body());
				throw new ApiException(403, "TURNSTILE_FAILED", "Verificacao anti-bot falhou. Tente novamente.");
			}
		} catch (ApiException e) {
			throw e;
		} catch (Exception e) {
			LOG.error("Erro ao validar Turnstile: {}", e.getMessage());
			// Falha de rede com o Cloudflare nao deve impedir o usuario de entrar.
		}
	}
}
