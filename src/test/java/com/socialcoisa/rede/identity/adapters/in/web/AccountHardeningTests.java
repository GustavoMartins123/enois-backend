package com.socialcoisa.rede.identity.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.socialcoisa.rede.identity.application.EmailService;
import com.socialcoisa.rede.shared.web.TestAuthHelper;
import com.socialcoisa.rede.shared.web.TestDatabase;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Account flows that must not reveal which addresses exist, must not grant admin through editable
 * fields, and must keep recovery tied to the confirmed address.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccountHardeningTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private EmailService emailService;

	private final List<String> resetTokens = new CopyOnWriteArrayList<>();

	@BeforeEach
	void setUp() {
		resetTokens.clear();
		when(emailService.sendVerificationEmail(anyString(), anyString(), anyString())).thenReturn(true);
		when(emailService.sendEmailChangeConfirmation(anyString(), anyString(), anyString())).thenReturn(true);
		org.mockito.Mockito.doAnswer(invocation -> {
			resetTokens.add(invocation.getArgument(2, String.class));
			return null;
		}).when(emailService).sendPasswordResetEmail(anyString(), anyString(), anyString());
	}

	@Test
	void renamingToAnAdminLikeNameIsRefusedAndGrantsNothing() throws Exception {
		TestAuthHelper.AuthSession session = new TestAuthHelper(mockMvc, objectMapper).authenticate("acch_rename");

		mockMvc.perform(patch("/me/profile")
				.header("X-Session-Token", session.token())
				.header("Idempotency-Key", "acch-rename")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("username", "Admin"))))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("USERNAME_RESERVED"));

		mockMvc.perform(get("/admin/admins").header("X-Session-Token", session.token()))
			.andExpect(status().isForbidden());
	}

	@Test
	void maintenanceTwoFactorResetRequiresAnAdmin() throws Exception {
		TestAuthHelper.AuthSession session = new TestAuthHelper(mockMvc, objectMapper).authenticate("acch_nonadmin");

		mockMvc.perform(post("/auth/admin/two-factor/reset-broken").header("X-Session-Token", session.token()))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.reset").doesNotExist());
	}

	@Test
	void unconfirmedEmailChangeKeepsRecoveryOnTheConfirmedAddress() throws Exception {
		TestAuthHelper.AuthSession session = new TestAuthHelper(mockMvc, objectMapper).authenticate("acch_mail");

		mockMvc.perform(post("/auth/email/change")
				.header("X-Session-Token", session.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("email", "acch_mail_new@test.com",
					"password", "Test1234!"))))
			.andExpect(status().isAccepted());

		mockMvc.perform(get("/auth/me").header("X-Session-Token", session.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value("acch_mail@test.com"));

		mockMvc.perform(post("/auth/password/forgot")
				.header("Idempotency-Key", "acch-forgot-new")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("email", "acch_mail_new@test.com"))))
			.andExpect(status().isAccepted());
		verify(emailService, never()).sendPasswordResetEmail(eq("acch_mail_new@test.com"), anyString(), anyString());
	}

	private JsonNode signup(String email, String password, String key) throws Exception {
		String response = mockMvc.perform(post("/auth/signup")
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(signupBody(email, password))))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(response);
	}

	private static Map<String, Object> signupBody(String email, String password) {
		Map<String, Object> body = new HashMap<>();
		body.put("name", "Account Hardening");
		body.put("role", "USER");
		body.put("email", email);
		body.put("password", password);
		body.put("acceptedTerms", true);
		body.put("birthDate", "1990-05-10");
		return body;
	}
}
