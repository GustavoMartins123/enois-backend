package com.socialcoisa.rede.identity.adapters.in.web;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.socialcoisa.rede.identity.adapters.out.oauth.OAuthProperties;
import com.socialcoisa.rede.identity.application.AuthResponse;
import com.socialcoisa.rede.identity.application.LoginCommand;
import com.socialcoisa.rede.identity.application.SignupCommand;
import com.socialcoisa.rede.identity.application.port.in.ChangeEmailUseCase;
import com.socialcoisa.rede.identity.application.port.in.ChangePasswordUseCase;
import com.socialcoisa.rede.identity.application.port.in.DeleteAccountUseCase;
import com.socialcoisa.rede.identity.application.port.in.GetAuthenticatedUserUseCase;
import com.socialcoisa.rede.identity.application.port.in.LoginUseCase;
import com.socialcoisa.rede.identity.application.port.in.LogoutUseCase;
import com.socialcoisa.rede.identity.application.port.in.OAuthStartResponse;
import com.socialcoisa.rede.identity.application.port.in.OAuthUseCase;
import com.socialcoisa.rede.identity.application.port.in.RefreshSessionUseCase;
import com.socialcoisa.rede.identity.application.port.in.RequestPasswordResetUseCase;
import com.socialcoisa.rede.identity.application.port.in.ResetPasswordUseCase;
import com.socialcoisa.rede.identity.application.LoginResult;
import com.socialcoisa.rede.identity.application.port.in.SignupUseCase;
import com.socialcoisa.rede.identity.application.port.in.TwoFactorLoginUseCase;
import com.socialcoisa.rede.identity.application.port.in.TwoFactorUseCase;
import com.socialcoisa.rede.identity.application.port.in.VerifyEmailUseCase;
import com.socialcoisa.rede.identity.domain.OAuthProvider;
import com.socialcoisa.rede.shared.web.ApiException;
import com.socialcoisa.rede.shared.web.StatusResponse;
import com.socialcoisa.rede.idempotency.application.CommandResponse;
import com.socialcoisa.rede.idempotency.application.IdempotencyApplicationService;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;

@RestController
@RequestMapping("/auth")
class AuthController {

	private final SignupUseCase signupUseCase;
	private final VerifyEmailUseCase verifyEmailUseCase;
	private final TwoFactorLoginUseCase twoFactorLoginUseCase;
	private final TwoFactorUseCase twoFactorUseCase;
	private final LoginUseCase loginUseCase;
	private final LogoutUseCase logoutUseCase;
	private final GetAuthenticatedUserUseCase getAuthenticatedUserUseCase;
	private final OAuthUseCase oAuthUseCase;
	private final RefreshSessionUseCase refreshSessionUseCase;
	private final RequestPasswordResetUseCase requestPasswordResetUseCase;
	private final ResetPasswordUseCase resetPasswordUseCase;
	private final ChangeEmailUseCase changeEmailUseCase;
	private final ChangePasswordUseCase changePasswordUseCase;
	private final DeleteAccountUseCase deleteAccountUseCase;
	private final IdempotencyApplicationService idempotency;
	private final com.socialcoisa.rede.identity.application.AuthThrottle throttle;
	private final OAuthProperties oAuthProperties;

	AuthController(SignupUseCase signupUseCase, VerifyEmailUseCase verifyEmailUseCase,
			TwoFactorLoginUseCase twoFactorLoginUseCase, TwoFactorUseCase twoFactorUseCase,
			LoginUseCase loginUseCase, LogoutUseCase logoutUseCase,
			GetAuthenticatedUserUseCase getAuthenticatedUserUseCase,
			OAuthUseCase oAuthUseCase,
			RefreshSessionUseCase refreshSessionUseCase,
			RequestPasswordResetUseCase requestPasswordResetUseCase,
			ResetPasswordUseCase resetPasswordUseCase,
			ChangeEmailUseCase changeEmailUseCase,
			ChangePasswordUseCase changePasswordUseCase,
			DeleteAccountUseCase deleteAccountUseCase,
			IdempotencyApplicationService idempotency,
			OAuthProperties oAuthProperties,
			com.socialcoisa.rede.identity.application.AuthThrottle throttle) {
		this.signupUseCase = signupUseCase;
		this.verifyEmailUseCase = verifyEmailUseCase;
		this.twoFactorLoginUseCase = twoFactorLoginUseCase;
		this.twoFactorUseCase = twoFactorUseCase;
		this.loginUseCase = loginUseCase;
		this.logoutUseCase = logoutUseCase;
		this.getAuthenticatedUserUseCase = getAuthenticatedUserUseCase;
		this.oAuthUseCase = oAuthUseCase;
		this.refreshSessionUseCase = refreshSessionUseCase;
		this.requestPasswordResetUseCase = requestPasswordResetUseCase;
		this.resetPasswordUseCase = resetPasswordUseCase;
		this.changeEmailUseCase = changeEmailUseCase;
		this.changePasswordUseCase = changePasswordUseCase;
		this.deleteAccountUseCase = deleteAccountUseCase;
		this.idempotency = idempotency;
		this.throttle = throttle;
		this.oAuthProperties = oAuthProperties;
	}

	@PostMapping("/signup")
	@RateLimiter(name = "auth")
	ResponseEntity<String> signup(@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
			@RequestHeader(value = "X-Forwarded-For", required = false) String forwardedFor,
			@RequestBody SignupRequest request) {
		String ip = forwardedFor == null || forwardedFor.isBlank() ? "desconhecido"
			: forwardedFor.split(",")[0].trim();
		throttle.exigeSignupPermitido(ip);
		String scope = authScope(request.email());
		String hash = idempotency.hash(request);
		return idempotency.execute(scope, "POST", "/api/auth/signup", idempotencyKey, hash, () -> new CommandResponse(201,
			signupUseCase.signup(new SignupCommand(request.name(), "USER", request.email(), request.password(),
				request.acceptedTerms()))));
	}

	/**
	 * Confirma o e-mail a partir do link enviado no cadastro e ja devolve a sessao: quem confirma
	 * nao precisa entrar de novo. Token expirado (410), ja usado (409) e invalido (404) tem codigos
	 * proprios para a tela explicar o que aconteceu.
	 */
	@PostMapping("/email/verify")
	@RateLimiter(name = "auth")
	ResponseEntity<AuthResponse> verifyEmail(@RequestBody VerifyEmailRequest request) {
		return ResponseEntity.ok(verifyEmailUseCase.verifyEmail(request.token()));
	}

	/**
	 * Reenvia o link de confirmacao. Responde 202 mesmo quando o e-mail nao existe ou ja foi
	 * confirmado, para nao virar um oraculo de quais contas existem.
	 */
	@PostMapping("/email/resend")
	@RateLimiter(name = "email-sending")
	ResponseEntity<StatusResponse> resendVerification(@RequestBody ResendVerificationRequest request) {
		verifyEmailUseCase.resendVerification(request.email());
		return ResponseEntity.accepted().body(new StatusResponse(null, "VERIFICATION_SENT"));
	}

	/**
	 * Senha correta nao basta quando a conta tem segundo fator: nesse caso responde 202 com o desafio
	 * (`twoFactorRequired`), e a sessao sai em {@code /auth/2fa/verify}.
	 */
	@PostMapping("/login")
	@RateLimiter(name = "auth")
	ResponseEntity<String> login(@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
			@RequestBody LoginRequest request) {
		String scope = authScope(request.email());
		String hash = idempotency.hash(request);
		throttle.exigeLoginPermitido(request.email());
		return idempotency.execute(scope, "POST", "/api/auth/login", idempotencyKey, hash, () -> {
			try {
				LoginResult result = loginUseCase.login(new LoginCommand(request.email(), request.password()));
				throttle.limpaLogin(request.email());
				return result.requiresTwoFactor()
					? new CommandResponse(202, Map.of("twoFactorRequired", true,
						"challengeId", result.challenge().challengeId(),
						"method", result.challenge().method(),
						"expiresAt", result.challenge().expiresAt().toString()))
					: new CommandResponse(200, result.session());
			} catch (ApiException falha) {
				if (falha.status() == 401) {
					throttle.registraFalhaLogin(request.email());
				}
				throw falha;
			}
		});
	}

	/** Conclui o login com o codigo do segundo fator (ou com um codigo de recuperacao). */
	@PostMapping("/2fa/verify")
	@RateLimiter(name = "auth")
	ResponseEntity<AuthResponse> verifyTwoFactor(@RequestBody VerifyTwoFactorRequest request) {
		return ResponseEntity.ok(twoFactorLoginUseCase.verifyTwoFactor(request.challengeId(), request.code()));
	}

	/** Reenvia o codigo por e-mail do desafio em andamento. */
	@PostMapping("/2fa/resend")
	@RateLimiter(name = "email-sending")
	ResponseEntity<StatusResponse> resendTwoFactor(@RequestBody ResendTwoFactorRequest request) {
		twoFactorLoginUseCase.resendTwoFactorCode(request.challengeId());
		return ResponseEntity.accepted().body(new StatusResponse(null, "CODE_SENT"));
	}

	@PostMapping("/logout")
	ResponseEntity<String> logout(@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
			@RequestHeader(value = "X-Session-Token", required = false) String token) {
		// O escopo da idempotencia e chave de tabela: vai como HASH do token, nunca o token vivo
		// em texto puro (quem le a tabela — backup, DBA — nao ganha uma sessao com isso).
		String scope = "session:" + (token == null ? "anonymous" : idempotency.hash(token.trim()));
		String hash = idempotency.hash(Map.of("token", scope));
		return idempotency.execute(scope, "POST", "/api/auth/logout", idempotencyKey, hash, () -> {
			logoutUseCase.logout(token);
			return new CommandResponse(200, Map.of("status", "LOGGED_OUT"));
		});
	}

	@GetMapping("/me")
	Object me(@RequestHeader("X-Session-Token") String token) {
		return getAuthenticatedUserUseCase.getAuthenticatedUser(token);
	}

	@GetMapping("/oauth/{provider}/start")
	@RateLimiter(name = "auth")
	Object oauthStart(@PathVariable String provider) {
		OAuthProvider oauthProvider = parseProvider(provider);
		return oAuthUseCase.start(oauthProvider);
	}

	/**
	 * Inicia a vinculacao de um provedor a conta logada. O state guardado aponta para o usuario, e o
	 * callback que usar esse state vincula em vez de entrar.
	 */
	@PostMapping("/oauth/{provider}/link")
	@RateLimiter(name = "auth")
	Object oauthStartLink(@PathVariable String provider, @RequestHeader(value = "X-Session-Token", required = false) String token) {
		OAuthProvider oauthProvider = parseProvider(provider);
		String userId = requireUserId(token);
		return oAuthUseCase.startLink(userId, oauthProvider);
	}

	// ------------------------------------------------------------------ segundo fator (gestao)

	@GetMapping("/2fa/status")
	TwoFactorUseCase.TwoFactorStatus twoFactorStatus(
			@RequestHeader(value = "X-Session-Token", required = false) String token) {
		return twoFactorUseCase.status(requireUserId(token));
	}

	/** Comeca a configuracao do aplicativo authenticator (segredo pendente + URI otpauth). */
	@PostMapping("/2fa/totp/setup")
	@RateLimiter(name = "auth")
	TwoFactorUseCase.TotpSetup startTotpSetup(
			@RequestHeader(value = "X-Session-Token", required = false) String token) {
		return twoFactorUseCase.startTotpSetup(requireUserId(token));
	}

	@PostMapping("/2fa/totp/enable")
	@RateLimiter(name = "auth")
	Map<String, Object> enableTotp(@RequestHeader(value = "X-Session-Token", required = false) String token,
			@RequestBody TwoFactorCodeRequest request) {
		List<String> backupCodes = twoFactorUseCase.enableTotp(requireUserId(token), request.code());
		return Map.of("status", "TOTP_ENABLED", "backupCodes", backupCodes);
	}

	/** Desativar exige a senha: uma sessao roubada nao pode desligar o segundo fator sozinha. */
	@PostMapping("/2fa/totp/disable")
	@RateLimiter(name = "auth")
	StatusResponse disableTotp(@RequestHeader(value = "X-Session-Token", required = false) String token,
			@RequestBody PasswordConfirmationRequest request) {
		twoFactorUseCase.disableTotp(requireUserId(token), request.password());
		return new StatusResponse("2fa", "TOTP_DISABLED");
	}

	/** Envia o codigo que confirma a ativacao do segundo fator por e-mail. */
	@PostMapping("/2fa/email/request")
	@RateLimiter(name = "email-sending")
	ResponseEntity<StatusResponse> requestEmailOtp(
			@RequestHeader(value = "X-Session-Token", required = false) String token) {
		twoFactorUseCase.requestEmailOtpSetup(requireUserId(token));
		return ResponseEntity.accepted().body(new StatusResponse("2fa", "CODE_SENT"));
	}

	@PostMapping("/2fa/email/confirm")
	@RateLimiter(name = "auth")
	StatusResponse confirmEmailOtp(@RequestHeader(value = "X-Session-Token", required = false) String token,
			@RequestBody TwoFactorCodeRequest request) {
		twoFactorUseCase.confirmEmailOtpSetup(requireUserId(token), request.code());
		return new StatusResponse("2fa", "EMAIL_OTP_ENABLED");
	}

	@PostMapping("/2fa/email/disable")
	@RateLimiter(name = "auth")
	StatusResponse disableEmailOtp(@RequestHeader(value = "X-Session-Token", required = false) String token,
			@RequestBody PasswordConfirmationRequest request) {
		twoFactorUseCase.disableEmailOtp(requireUserId(token), request.password());
		return new StatusResponse("2fa", "EMAIL_OTP_DISABLED");
	}

	/** Provedores vinculados a conta (a tela mostra e permite desvincular). */
	@GetMapping("/oauth/providers")
	Object oauthProviders(@RequestHeader(value = "X-Session-Token", required = false) String token) {
		return oAuthUseCase.linkedProviders(requireUserId(token));
	}

	@DeleteMapping("/oauth/{provider}/link")
	@RateLimiter(name = "auth")
	Object oauthUnlink(@PathVariable String provider, @RequestHeader(value = "X-Session-Token", required = false) String token) {
		OAuthProvider oauthProvider = parseProvider(provider);
		oAuthUseCase.unlink(requireUserId(token), oauthProvider);
		return new StatusResponse(provider, "UNLINKED");
	}

	/**
	 * Callback do provedor. O `state` e conferido no servidor (existe, e do mesmo provedor, dentro da
	 * validade e de uso unico) — o cookie nao serve para isso, porque o POST cross-site da Apple nem
	 * o envia com SameSite=Lax.
	 */
	@GetMapping("/oauth/{provider}/callback")
	@RateLimiter(name = "auth")
	Object oauthCallback(@PathVariable String provider, @RequestParam(required = false) String code,
			@RequestParam(required = false) String state, @RequestParam(required = false) String error) {
		// Este callback e uma NAVIGACAO do browser (o provedor redireciona para ca). Qualquer
		// falha vira redirect para a tela de callback do app com mensagem legivel — JSON cru
		// aqui deixa a pessoa olhando um erro sem saida (relato de suporte: "Requisicao
		// invalida" ao importar o perfil do LinkedIn).
		try {
			OAuthProvider oauthProvider = parseProvider(provider);
			if (StringUtils.hasText(error) || !StringUtils.hasText(code)) {
				return finishOAuthError(oauthProvider, error);
			}
			return finishOAuth(oAuthUseCase.callback(oauthProvider, code, state, null));
		}
		catch (ApiException e) {
			return finishOAuthError(parseProvider(provider), e.code());
		}
	}

	/**
	 * Apple envia o callback via POST (response_mode=form_post), com code/state/user no corpo.
	 */
	@PostMapping("/oauth/{provider}/callback")
	@RateLimiter(name = "auth")
	Object oauthCallbackPost(@PathVariable String provider, @RequestParam(required = false) String code,
			@RequestParam(required = false) String state,
			@RequestParam(required = false, name = "id_token") String idToken,
			@RequestParam(required = false, name = "user") String userPayload,
			@RequestParam(required = false) String error) {
		OAuthProvider oauthProvider = parseProvider(provider);
		if (StringUtils.hasText(error)) {
			return finishOAuthError(oauthProvider, error);
		}
		// Preferimos o code: a troca comprova que o token veio para este cliente, e o `nonce` do
		// id_token resultante e conferido contra o state.
		AuthResponse auth = StringUtils.hasText(code)
			? oAuthUseCase.callback(oauthProvider, code, state, userPayload)
			: oAuthUseCase.tokenLogin(oauthProvider, idToken, state);
		return finishOAuth(auth);
	}

	private String requireUserId(String token) {
		if (!StringUtils.hasText(token)) {
			throw new ApiException(401, "UNAUTHENTICATED", "Sessao invalida ou expirada.");
		}
		return getAuthenticatedUserUseCase.getAuthenticatedUser(token).id();
	}

	private Object finishOAuth(AuthResponse auth) {
		String redirectUrl = oAuthProperties.getFrontendRedirectUrl();
		if (StringUtils.hasText(redirectUrl)) {
			String location = redirectUrl
				+ "?token=" + auth.token()
				+ "&refreshToken=" + auth.refreshToken()
				+ "&expiresAt=" + auth.expiresAt()
				+ "&refreshExpiresAt=" + auth.refreshExpiresAt();
			return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(location)).build();
		}
		return auth;
	}

	/**
	 * Cancelamento ou falha na tela do provedor volta para o app com um codigo legivel, em vez de
	 * estourar um erro tecnico no meio do redirect.
	 */
	private Object finishOAuthError(OAuthProvider provider, String error) {
		String code = StringUtils.hasText(error) ? error.trim().toUpperCase(Locale.ROOT) : "OAUTH_FAILED";
		String redirectUrl = oAuthProperties.getFrontendRedirectUrl();
		if (StringUtils.hasText(redirectUrl)) {
			return ResponseEntity.status(HttpStatus.FOUND)
				.location(URI.create(redirectUrl + "?error=" + urlEncode(code) + "&provider=" + provider.name()))
				.build();
		}
		throw new ApiException(401, "OAUTH_" + code, "Nao foi possivel concluir o login com " + provider.name() + ".");
	}

	private String urlEncode(String value) {
		return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
	}

	@PostMapping("/refresh")
	Object refreshSession(@RequestBody RefreshRequest request) {
		return refreshSessionUseCase.refresh(request.refreshToken());
	}

	@PostMapping("/oauth/{provider}/token")
	@RateLimiter(name = "auth")
	Object oauthToken(@PathVariable String provider, @RequestBody OAuthTokenRequest request) {
		OAuthProvider oauthProvider = parseProvider(provider);
		// O `state` (obtido em /oauth/{provider}/start) amarra o id_token ao pedido: sem ele o token
		// poderia ter sido emitido para outro fluxo e nao ha nonce para conferir.
		return oAuthUseCase.tokenLogin(oauthProvider, request.idToken(), request.state());
	}

	record OAuthTokenRequest(String idToken, String state) {
	}

	record RefreshRequest(String refreshToken) {
	}

	private OAuthProvider parseProvider(String value) {
		try {
			return OAuthProvider.valueOf(value.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new com.socialcoisa.rede.shared.web.ApiException(400, "INVALID_OAUTH_PROVIDER",
				"Provedor OAuth invalido: " + value);
		}
	}

	@PostMapping("/password/forgot")
	@RateLimiter(name = "email-sending")
	ResponseEntity<String> forgotPassword(@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
			@RequestBody ForgotPasswordRequest request) {
		String scope = authScope(request.email());
		String hash = idempotency.hash(request);
		return idempotency.execute(scope, "POST", "/api/auth/password/forgot", idempotencyKey, hash,
			() -> {
				requestPasswordResetUseCase.requestReset(request.email());
				return new CommandResponse(202, Map.of("status", "RESET_REQUEST_ACCEPTED"));
			});
	}

	@PostMapping("/password/reset")
	@RateLimiter(name = "email-sending")
	ResponseEntity<String> resetPassword(@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
			@RequestBody ResetPasswordRequest request) {
		String scope = "reset:" + (StringUtils.hasText(request.token()) ? idempotency.hash(request.token().trim())
			: "anonymous");
		String hash = idempotency.hash(request);
		return idempotency.execute(scope, "POST", "/api/auth/password/reset", idempotencyKey, hash,
			() -> {
				resetPasswordUseCase.reset(request.token(), request.password());
				return new CommandResponse(200, Map.of("status", "PASSWORD_RESET_ACCEPTED"));
			});
	}

	@PostMapping("/email/change")
	Object changeEmail(@RequestHeader(value = "X-Session-Token", required = false) String token,
			@RequestBody ChangeEmailRequest request) {
		changeEmailUseCase.changeEmail(token, request.email(), request.password());
		return new StatusResponse("me", "EMAIL_CHANGED");
	}

	@PostMapping("/password/change")
	@RateLimiter(name = "auth")
	Object changePassword(@RequestHeader(value = "X-Session-Token", required = false) String token,
			@RequestBody ChangePasswordRequest request) {
		changePasswordUseCase.changePassword(token, request.currentPassword(), request.newPassword());
		return new StatusResponse("me", "PASSWORD_CHANGED");
	}

	@PostMapping("/account/delete")
	Object deleteAccount(@RequestHeader(value = "X-Session-Token", required = false) String token,
			@RequestBody DeleteAccountRequest request) {
		deleteAccountUseCase.deleteAccount(token, request.password());
		return new StatusResponse("me", "ACCOUNT_DELETED");
	}

	record ChangeEmailRequest(String email, String password) {
	}

	record ChangePasswordRequest(String currentPassword, String newPassword) {
	}

	record DeleteAccountRequest(String password) {
	}

	private String authScope(String email) {
		return "auth:" + (StringUtils.hasText(email) ? email.trim().toLowerCase(Locale.ROOT) : "anonymous");
	}

	record ForgotPasswordRequest(String email) {
	}

	record ResetPasswordRequest(String token, String password) {
	}

	record VerifyEmailRequest(String token) {
	}

	record ResendVerificationRequest(String email) {
	}

	record VerifyTwoFactorRequest(String challengeId, String code) {
	}

	record ResendTwoFactorRequest(String challengeId) {
	}

	record TwoFactorCodeRequest(String code) {
	}

	record PasswordConfirmationRequest(String password) {
	}
}
