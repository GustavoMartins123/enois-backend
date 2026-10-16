package com.socialcoisa.rede.identity.adapters.out.provider;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.socialcoisa.rede.identity.application.port.out.IdentityVerificationProvider;
import com.socialcoisa.rede.identity.domain.IdentityVerificationStatus;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Provedor do Login Único do gov.br (OAuth 2.0 / OpenID Connect, Authorization Code).
 *
 * Só fica ATIVO com credenciais configuradas (rede.identity.govbr.client-id etc.). Sem elas, o
 * registro considera o provedor indisponível e a verificação aparece como indisponível no app —
 * nunca aprovada por simulação. Endpoints conforme o roteiro oficial de integração.
 */
@Component
public class GovBrIdentityProvider implements IdentityVerificationProvider {

	private final String baseUrl;
	private final String clientId;
	private final String clientSecret;
	private final String redirectUri;
	private final ObjectMapper objectMapper;
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	public GovBrIdentityProvider(
			@Value("${rede.identity.govbr.base-url:https://sso.staging.acesso.gov.br}") String baseUrl,
			@Value("${rede.identity.govbr.client-id:}") String clientId,
			@Value("${rede.identity.govbr.client-secret:}") String clientSecret,
			@Value("${rede.identity.govbr.redirect-uri:}") String redirectUri,
			ObjectMapper objectMapper) {
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		this.clientId = clientId;
		this.clientSecret = clientSecret;
		this.redirectUri = redirectUri;
		this.objectMapper = objectMapper;
	}

	@Override
	public String name() {
		return "govbr";
	}

	@Override
	public boolean available() {
		return StringUtils.hasText(clientId) && StringUtils.hasText(clientSecret) && StringUtils.hasText(redirectUri);
	}

	@Override
	public Start start(String userId, String consentVersion) {
		// `state` imprevisível e de uso único: é ele que liga o retorno à tentativa certa.
		String state = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
		String url = baseUrl + "/authorize"
			+ "?response_type=code"
			+ "&client_id=" + enc(clientId)
			+ "&redirect_uri=" + enc(redirectUri)
			+ "&scope=" + enc("openid email profile")
			+ "&state=" + enc(state)
			+ "&nonce=" + enc(state);
		return new Start(state, url);
	}

	/** Login Único responde por callback, não por consulta: não há polling. */
	@Override
	public Optional<Outcome> fetchOutcome(String attemptId) {
		return Optional.empty();
	}

	@Override
	public Optional<Outcome> handleCallback(String state, String code) {
		try {
			String form = "grant_type=authorization_code"
				+ "&code=" + enc(code)
				+ "&redirect_uri=" + enc(redirectUri);
			String basic = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
			HttpRequest tokenRequest = HttpRequest.newBuilder(URI.create(baseUrl + "/token"))
				.timeout(Duration.ofSeconds(15))
				.header("Content-Type", "application/x-www-form-urlencoded")
				.header("Authorization", "Basic " + basic)
				.POST(HttpRequest.BodyPublishers.ofString(form))
				.build();
			HttpResponse<String> tokenResponse = http.send(tokenRequest, HttpResponse.BodyHandlers.ofString());
			if (tokenResponse.statusCode() / 100 != 2) {
				// Falha técnica não vira aprovação; fica em ERROR (permite nova tentativa).
				return Optional.of(new Outcome(IdentityVerificationStatus.ERROR, "PROVIDER_TOKEN_FAILED"));
			}
			JsonNode token = objectMapper.readTree(tokenResponse.body());
			String accessToken = token.path("access_token").asString();
			if (!StringUtils.hasText(accessToken)) {
				return Optional.of(new Outcome(IdentityVerificationStatus.ERROR, "PROVIDER_TOKEN_FAILED"));
			}

			HttpRequest userInfoRequest = HttpRequest.newBuilder(URI.create(baseUrl + "/userinfo"))
				.timeout(Duration.ofSeconds(15))
				.header("Authorization", "Bearer " + accessToken)
				.GET()
				.build();
			HttpResponse<String> userInfoResponse = http.send(userInfoRequest, HttpResponse.BodyHandlers.ofString());
			if (userInfoResponse.statusCode() / 100 != 2) {
				return Optional.of(new Outcome(IdentityVerificationStatus.ERROR, "PROVIDER_USERINFO_FAILED"));
			}
			JsonNode profile = objectMapper.readTree(userInfoResponse.body());
			// Só o suficiente para saber que a identidade foi confirmada: o `sub` do provedor.
			if (StringUtils.hasText(profile.path("sub").asString())) {
				return Optional.of(new Outcome(IdentityVerificationStatus.VERIFIED, null));
			}
			return Optional.of(new Outcome(IdentityVerificationStatus.ERROR, "PROVIDER_EMPTY_SUB"));
		}
		catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			return Optional.of(new Outcome(IdentityVerificationStatus.ERROR, "PROVIDER_UNAVAILABLE"));
		}
		catch (Exception failure) {
			// Não registra corpo/resposta do provedor (sem dados pessoais nem tokens nos logs).
			return Optional.of(new Outcome(IdentityVerificationStatus.ERROR, "PROVIDER_UNAVAILABLE"));
		}
	}

	private static String enc(String value) {
		return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
	}
}
