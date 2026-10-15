package com.socialcoisa.rede.identity.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.socialcoisa.rede.identity.application.port.out.TokenValidationResult;
import com.socialcoisa.rede.identity.application.port.out.TokenValidator;
import com.socialcoisa.rede.identity.domain.OAuthProvider;
import com.socialcoisa.rede.shared.web.TestAuthHelper;
import com.socialcoisa.rede.shared.web.TestDatabase;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Regras do login social: state emitido e conferido no servidor, nenhum vinculo automatico por
 * e-mail declarado e desvinculacao que nao deixa a conta sem acesso.
 *
 * A validacao do id_token em si depende do provedor (chaves publicas reais), entao aqui o validador
 * do Google e substituido: o que queremos provar e o comportamento da aplicacao depois dele.
 */
@SpringBootTest
@AutoConfigureMockMvc
// O perfil de teste (dev) define client-ids vazios; aqui eles precisam existir para /start montar a
// URL e registrar o state. Nao ha chamada real ao provedor: o validador e substituido por mock.
@TestPropertySource(properties = {
		"rede.oauth.google.client-id=test-client-id",
		"rede.oauth.google.client-secret=test-client-secret",
		"rede.oauth.google.redirect-uri=http://localhost:8081/api/auth/oauth/google/callback",
		"rede.oauth.apple.client-id=test-apple-client-id",
		"rede.oauth.apple.redirect-uri=http://localhost:8081/api/auth/oauth/apple/callback",
		"rede.oauth.frontend-redirect-url=http://localhost:5173/auth/callback" })
class OAuthHardeningTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	private TestAuthHelper auth;

	@MockitoBean(name = "googleTokenValidator")
	private TokenValidator googleValidator;

	@BeforeEach
	void setUp() {
		auth = new TestAuthHelper(mockMvc, objectMapper);
	}

	@Test
	void requiresAStateIssuedByUs() throws Exception {
		// Sem state: nao da para saber se o pedido comecou aqui.
		mockMvc.perform(get("/auth/oauth/google/callback").param("code", "codigo-qualquer"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_OAUTH_STATE"));

		// State inventado.
		mockMvc.perform(get("/auth/oauth/google/callback").param("code", "codigo-qualquer").param("state", "state_inventado"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_OAUTH_STATE"));

		// State expirado (a linha existe, mas fora da validade).
		TestDatabase.insertOAuthRequest("state_vencido", "GOOGLE", "nonce_1", Instant.now().minusSeconds(60));
		mockMvc.perform(get("/auth/oauth/google/callback").param("code", "codigo-qualquer").param("state", "state_vencido"))
			.andExpect(status().isGone())
			.andExpect(jsonPath("$.code").value("OAUTH_STATE_EXPIRED"));

		// State de outro provedor nao vale aqui.
		TestDatabase.insertOAuthRequest("state_apple", "APPLE", "nonce_2", Instant.now().plusSeconds(600));
		mockMvc.perform(get("/auth/oauth/google/callback").param("code", "codigo-qualquer").param("state", "state_apple"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_OAUTH_STATE"));
	}

	@Test
	void startRegistersStateAndNonceOnTheServer() throws Exception {
		MvcResult result = mockMvc.perform(get("/auth/oauth/google/start"))
			.andExpect(status().isOk())
			.andReturn();

		JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
		String authorizationUrl = body.get("authorizationUrl").asText();

		assertThat(authorizationUrl).contains("state=").contains("nonce=");
		// O state devolvido ja esta registrado: um callback com ele nao e recusado por state invalido.
		mockMvc.perform(get("/auth/oauth/google/callback").param("code", "codigo-qualquer")
				.param("state", body.get("state").asText()))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void linksAnExistingAccountWhenTheProviderConfirmsTheEmail() throws Exception {
		// Conta criada com senha; o Google confirma que o e-mail e dela.
		var existing = auth.authenticate("ja.existe");
		when(googleValidator.validate(eq(OAuthProvider.GOOGLE), anyString(), anyString()))
			.thenReturn(Optional.of(new TokenValidationResult("google-sub-existente", "ja.existe@test.com",
				"Ja Existe", true)));

		MvcResult result = mockMvc.perform(get("/auth/oauth/google/callback")
				.param("code", "codigo-valido").param("state", startState()))
			.andExpect(status().isFound())
			.andReturn();
		String sessionToken = tokenFromRedirect(result.getResponse().getHeader("Location"));

		// Mesma conta (nao duplicou), agora vinculada, com e-mail confirmado e 2FA delegado ao provedor.
		mockMvc.perform(get("/auth/me").header("X-Session-Token", sessionToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(existing.userId()))
			.andExpect(jsonPath("$.emailVerified").value(true))
			.andExpect(jsonPath("$.twoFactorEnabled").value(true))
			.andExpect(jsonPath("$.identityVerified").value(true));

		mockMvc.perform(get("/auth/oauth/providers").header("X-Session-Token", existing.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].provider").value("GOOGLE"));

		mockMvc.perform(get("/auth/2fa/status").header("X-Session-Token", sessionToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.providerSecondFactor").value(true))
			// O provedor cobre a segunda etapa: nao oferecemos desativar o que nao foi ativado aqui.
			.andExpect(jsonPath("$.totpEnabled").value(false))
			.andExpect(jsonPath("$.emailOtpEnabled").value(false));
	}

	@Test
	void refusesToLinkWhenTheProviderDoesNotConfirmTheEmail() throws Exception {
		// Sem a garantia do provedor, o e-mail declarado nao entrega a conta para ninguem.
		var existing = auth.authenticate("nao.confirmado");
		when(googleValidator.validate(eq(OAuthProvider.GOOGLE), anyString(), anyString()))
			.thenReturn(Optional.of(new TokenValidationResult("google-sub-nao-confirmado", "nao.confirmado@test.com",
				"Nao Confirmado", false)));

		mockMvc.perform(get("/auth/oauth/google/callback")
				.param("code", "codigo-valido").param("state", startState()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("ACCOUNT_EXISTS_UNVERIFIED_EMAIL"));

		mockMvc.perform(get("/auth/oauth/providers").header("X-Session-Token", existing.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isEmpty());
	}

	@Test
	void createsAProviderAccountAlreadyVerifiedAndProtectsUnlink() throws Exception {
		when(googleValidator.validate(eq(OAuthProvider.GOOGLE), anyString(), anyString()))
			.thenReturn(Optional.of(new TokenValidationResult("google-sub-novo", "nova.pessoa@example.com",
				"Nova Pessoa", true)));

		String state = startState();

		// Com frontend-redirect-url configurado o callback devolve a sessao na URL de retorno.
		MvcResult result = mockMvc.perform(get("/auth/oauth/google/callback")
				.param("code", "codigo-valido").param("state", state))
			.andExpect(status().isFound())
			.andReturn();

		String sessionToken = tokenFromRedirect(result.getResponse().getHeader("Location"));

		// Quem entra pelo provedor nao passa pela confirmacao de e-mail.
		mockMvc.perform(get("/auth/me").header("X-Session-Token", sessionToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.emailVerified").value(true));

		// A conta nasce sem senha utilizavel, com um unico provedor: desvincular tiraria o acesso.
		mockMvc.perform(delete("/auth/oauth/google/link").header("X-Session-Token", sessionToken))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("LAST_LOGIN_METHOD"));

		mockMvc.perform(get("/auth/oauth/providers").header("X-Session-Token", sessionToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].provider").value("GOOGLE"))
			.andExpect(jsonPath("$[0].hasUsablePassword").value(false));
	}

	@Test
	void unlinkIsAllowedWhenThereIsAnotherWayIn() throws Exception {
		var session = auth.authenticate("tem.senha");
		TestDatabase.insertExternalIdentity(session.userId(), "GOOGLE", "google-sub-temsenha", "tem.senha@test.com");

		mockMvc.perform(delete("/auth/oauth/google/link").header("X-Session-Token", session.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UNLINKED"));

		// Sem vinculo, desvincular de novo responde "nao vinculado".
		mockMvc.perform(delete("/auth/oauth/google/link").header("X-Session-Token", session.token()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("OAUTH_NOT_LINKED"));
	}

	@Test
	void linkingRequiresASession() throws Exception {
		mockMvc.perform(post("/auth/oauth/google/link")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/auth/oauth/providers")).andExpect(status().isUnauthorized());
	}

	private String tokenFromRedirect(String location) {
		assertThat(location).isNotNull();
		return java.util.Arrays.stream(location.split("[?&#]"))
			.filter(part -> part.startsWith("token="))
			.map(part -> part.substring("token=".length()))
			.findFirst()
			.orElseThrow(() -> new AssertionError("sem token na URL de retorno: " + location));
	}

	private String startState() throws Exception {
		MvcResult result = mockMvc.perform(get("/auth/oauth/google/start")).andExpect(status().isOk()).andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("state").asText();
	}
}