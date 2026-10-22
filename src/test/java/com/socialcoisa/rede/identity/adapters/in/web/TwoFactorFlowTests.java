package com.socialcoisa.rede.identity.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.socialcoisa.rede.identity.application.EmailService;
import com.socialcoisa.rede.shared.security.Totp;
import com.socialcoisa.rede.shared.web.TestAuthHelper;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Segundo fator de ponta a ponta: ativacao por aplicativo, login com desafio, codigo por e-mail,
 * codigo de recuperacao e a regra de credibilidade (sem verificacao, reputacao zerada).
 */
@SpringBootTest
@AutoConfigureMockMvc
class TwoFactorFlowTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	/** Espiao do envio: e por onde o teste ve o codigo que so existe no e-mail. */
	@MockitoBean
	private EmailService emailService;

	private String lastEmailedCode;

	@BeforeEach
	void setUp() {
		lastEmailedCode = null;
		when(emailService.sendTwoFactorCode(anyString(), anyString(), anyString())).thenAnswer(invocation -> {
			lastEmailedCode = invocation.getArgument(2, String.class);
			return true;
		});
		when(emailService.sendVerificationEmail(anyString(), anyString(), anyString())).thenReturn(true);
	}

	@Test
	void totpSetupRequiresAConfirmedCodeAndThenChallengesTheLogin() throws Exception {
		var session = new TestAuthHelper(mockMvc, objectMapper).authenticate("two_factor_totp");

		// 1) setup devolve o segredo e a URI do aplicativo
		MvcResult setup = mockMvc.perform(post("/auth/2fa/totp/setup").header("X-Session-Token", session.token())
				.header("Idempotency-Key", "totp-setup-1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.secret").isNotEmpty())
			.andExpect(jsonPath("$.otpauthUri").value(org.hamcrest.Matchers.startsWith("otpauth://totp/")))
			.andReturn();
		String secret = objectMapper.readTree(setup.getResponse().getContentAsString()).get("secret").asText();

		// 2) codigo errado nao ativa
		mockMvc.perform(post("/auth/2fa/totp/enable").header("X-Session-Token", session.token())
				.header("Idempotency-Key", "totp-enable-bad")
				.contentType(MediaType.APPLICATION_JSON)
				.content(code("000000")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_TWO_FACTOR_CODE"));

		// 3) codigo do app ativa e devolve os codigos de recuperacao (mostrados uma vez)
		MvcResult enabled = mockMvc.perform(post("/auth/2fa/totp/enable").header("X-Session-Token", session.token())
				.header("Idempotency-Key", "totp-enable-ok")
				.contentType(MediaType.APPLICATION_JSON)
				.content(code(validCode(secret))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("TOTP_ENABLED"))
			.andExpect(jsonPath("$.backupCodes.length()").value(8))
			.andReturn();
		List<String> backupCodes = objectMapper.readTree(enabled.getResponse().getContentAsString())
			.get("backupCodes")
			.valueStream()
			.map(JsonNode::asText)
			.toList();

		mockMvc.perform(get("/auth/2fa/status").header("X-Session-Token", session.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totpEnabled").value(true))
			.andExpect(jsonPath("$.identityVerified").value(true))
			.andExpect(jsonPath("$.backupCodesLeft").value(8));

		// 4) o login por senha passa a exigir o segundo fator (nao vem token)
		MvcResult login = mockMvc.perform(post("/auth/login").header("Idempotency-Key", "totp-login-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginPayload("two_factor_totp@test.com")))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.twoFactorRequired").value(true))
			.andExpect(jsonPath("$.method").value("TOTP"))
			.andReturn();
		String challengeId = objectMapper.readTree(login.getResponse().getContentAsString()).get("challengeId").asText();

		// 5) codigo errado nao entrega sessao
		mockMvc.perform(post("/auth/2fa/verify").header("Idempotency-Key", "totp-verify-bad")
				.contentType(MediaType.APPLICATION_JSON).content(challenge(challengeId, "123456")))
			.andExpect(status().isBadRequest());

		// 6) codigo certo conclui o login
		MvcResult verified = mockMvc.perform(post("/auth/2fa/verify").header("Idempotency-Key", "totp-verify-ok")
				.contentType(MediaType.APPLICATION_JSON).content(challenge(challengeId, validCode(secret))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.token").isNotEmpty())
			.andExpect(jsonPath("$.user.twoFactorEnabled").value(true))
			.andReturn();

		// 7) o desafio e de uso unico
		mockMvc.perform(post("/auth/2fa/verify").header("Idempotency-Key", "totp-verify-replay")
				.contentType(MediaType.APPLICATION_JSON).content(challenge(challengeId, validCode(secret))))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("TWO_FACTOR_CHALLENGE_ALREADY_USED"));

		// 8) codigo de recuperacao tambem entra (e vale uma vez so)
		String sessionToken = objectMapper.readTree(verified.getResponse().getContentAsString()).get("token").asText();
		assertThat(sessionToken).isNotBlank();

		MvcResult recoveryLogin = mockMvc.perform(post("/auth/login").header("Idempotency-Key", "totp-login-2")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginPayload("two_factor_totp@test.com")))
			.andExpect(status().isAccepted())
			.andReturn();
		String recoveryChallenge = objectMapper.readTree(recoveryLogin.getResponse().getContentAsString())
			.get("challengeId").asText();

		mockMvc.perform(post("/auth/2fa/verify").header("Idempotency-Key", "totp-verify-recovery")
				.contentType(MediaType.APPLICATION_JSON).content(challenge(recoveryChallenge, backupCodes.get(0))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.token").isNotEmpty());

		mockMvc.perform(get("/auth/2fa/status").header("X-Session-Token", sessionToken))
			.andExpect(jsonPath("$.backupCodesLeft").value(7));
	}

	@Test
	void emailSecondFactorSendsAndChecksTheCode() throws Exception {
		var session = new TestAuthHelper(mockMvc, objectMapper).authenticate("two_factor_email");

		mockMvc.perform(post("/auth/2fa/email/request").header("X-Session-Token", session.token())
				.header("Idempotency-Key", "email-2fa-1"))
			.andExpect(status().isAccepted());
		assertThat(lastEmailedCode).isNotNull();

		mockMvc.perform(post("/auth/2fa/email/confirm").header("X-Session-Token", session.token())
				.header("Idempotency-Key", "email-2fa-2")
				.contentType(MediaType.APPLICATION_JSON)
				.content(code(lastEmailedCode)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("EMAIL_OTP_ENABLED"));

		// Login: a senha leva ao desafio por e-mail, com o codigo enviado na hora.
		MvcResult login = mockMvc.perform(post("/auth/login").header("Idempotency-Key", "email-login-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginPayload("two_factor_email@test.com")))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.method").value("EMAIL"))
			.andReturn();
		String challengeId = objectMapper.readTree(login.getResponse().getContentAsString()).get("challengeId").asText();
		assertThat(lastEmailedCode).isNotNull();

		mockMvc.perform(post("/auth/2fa/verify").header("Idempotency-Key", "email-verify-1")
				.contentType(MediaType.APPLICATION_JSON).content(challenge(challengeId, lastEmailedCode)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.token").isNotEmpty());
	}

	@Test
	void disablingRequiresThePasswordAndReactivatesTheChallenge() throws Exception {
		var session = new TestAuthHelper(mockMvc, objectMapper).authenticate("two_factor_disable");

		MvcResult setup = mockMvc.perform(post("/auth/2fa/totp/setup").header("X-Session-Token", session.token())
				.header("Idempotency-Key", "disable-setup"))
			.andExpect(status().isOk())
			.andReturn();
		String secret = objectMapper.readTree(setup.getResponse().getContentAsString()).get("secret").asText();
		mockMvc.perform(post("/auth/2fa/totp/enable").header("X-Session-Token", session.token())
				.header("Idempotency-Key", "disable-enable")
				.contentType(MediaType.APPLICATION_JSON).content(code(validCode(secret))))
			.andExpect(status().isOk());

		// Sem a senha, ninguem desliga o segundo fator.
		mockMvc.perform(post("/auth/2fa/totp/disable").header("X-Session-Token", session.token())
				.header("Idempotency-Key", "disable-bad-password")
				.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("password", "errada"))))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/auth/2fa/totp/disable").header("X-Session-Token", session.token())
				.header("Idempotency-Key", "disable-ok")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("password", "Test1234!"))))
			.andExpect(status().isOk());

		// Desligado: o login volta a entregar sessao direto.
		mockMvc.perform(post("/auth/login").header("Idempotency-Key", "disable-login")
				.contentType(MediaType.APPLICATION_JSON).content(loginPayload("two_factor_disable@test.com")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.token").isNotEmpty());
	}

	@Test
	void accountLosesAccessToProtectedFeaturesWhenVerificationIsGone() throws Exception {
		var session = new TestAuthHelper(mockMvc, objectMapper).authenticate("two_factor_reputation");

		// Conta confirmada: /auth/me mostra o estado e as areas protegidas respondem normalmente.
		JsonNode verified = objectMapper.readTree(mockMvc.perform(get("/auth/me")
				.header("X-Session-Token", session.token()))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString());
		assertThat(verified.get("identityVerified").asBoolean()).isTrue();
		mockMvc.perform(get("/notifications").header("X-Session-Token", session.token()))
			.andExpect(status().isOk());

		// Voltando ao estado pendente (sem e-mail confirmado, sem 2FA), a segunda barreira do
		// CurrentUserResolver barra as funcionalidades protegidas — /auth/me segue acessivel porque e
		// onde a propria pessoa ve o estado da conta.
		com.socialcoisa.rede.shared.web.TestDatabase.clearEmailVerification(session.userId());

		mockMvc.perform(get("/notifications").header("X-Session-Token", session.token()))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

		JsonNode unverified = objectMapper.readTree(mockMvc.perform(get("/auth/me")
				.header("X-Session-Token", session.token()))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString());
		assertThat(unverified.get("identityVerified").asBoolean()).isFalse();

		// Ativando o segundo fator, a conta volta a ser considerada verificada.
		com.socialcoisa.rede.shared.web.TestDatabase.enableTwoFactor(session.userId(), Instant.now());
		mockMvc.perform(get("/notifications").header("X-Session-Token", session.token()))
			.andExpect(status().isOk());
	}

	private String validCode(String secret) {
		return Totp.codeAt(secret, Instant.now().getEpochSecond() / 30);
	}

	private String code(String value) throws Exception {
		return objectMapper.writeValueAsString(Map.of("code", value));
	}

	private String challenge(String challengeId, String value) throws Exception {
		return objectMapper.writeValueAsString(Map.of("challengeId", challengeId, "code", value));
	}

	private String loginPayload(String email) throws Exception {
		return objectMapper.writeValueAsString(Map.of("email", email, "password", "Test1234!"));
	}
}