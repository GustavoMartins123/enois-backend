package com.socialcoisa.rede.support.adapters.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import com.socialcoisa.rede.shared.web.AdminGuard;
import com.socialcoisa.rede.shared.web.ModeratorGuard;
import com.socialcoisa.rede.shared.web.TestAuthHelper;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class SupportRouteAuthorizationTests {
	@Autowired MockMvc mockMvc;
	@Autowired ObjectMapper objectMapper;
	@MockitoSpyBean SupportController controller;
	@MockitoSpyBean AdminGuard adminGuard;
	@MockitoSpyBean ModeratorGuard moderatorGuard;

	@BeforeEach
	void resetObservation() {
		clearInvocations(controller, adminGuard, moderatorGuard);
	}

	@Test
	void semSessaoNaoExecutaControllerComParametrosDeMatriz() throws Exception {
		mockMvc.perform(get(URI.create("/admin;v=1/support/github")))
			.andExpect(status().isUnauthorized());
		verifyNoInteractions(controller);
	}

	@Test
	void usuarioComumNaoExecutaLeituraOuEscritaProtegida() throws Exception {
		var user = new TestAuthHelper(mockMvc, objectMapper).authenticate("route_policy_plain");
		clearInvocations(controller, adminGuard, moderatorGuard);
		for (String path : List.of("/admin/support/github", "/%61dmin/support/github",
				"/admin;v=1/support/github", "/admin/support/github;v=1")) {
			mockMvc.perform(get(URI.create(path)).header("X-Session-Token", user.token()))
				.andExpect(status().isForbidden());
			mockMvc.perform(put(URI.create(path)).header("X-Session-Token", user.token())
					.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isForbidden());
		}
		verifyNoInteractions(controller);
	}

	@Test
	void handlerExclusivoDeAdminUsaGuardaDeAdmin() throws Exception {
		var user = new TestAuthHelper(mockMvc, objectMapper).authenticate("route_policy_role");
		clearInvocations(controller, adminGuard, moderatorGuard);
		mockMvc.perform(get(URI.create("/admin/support/github;v=1"))
				.header("X-Session-Token", user.token()))
			.andExpect(status().isForbidden());
		verify(adminGuard).requireAdmin(any());
		verify(moderatorGuard, never()).requireModerator(any());
		verifyNoInteractions(controller);
	}
}
