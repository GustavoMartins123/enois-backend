package com.socialcoisa.rede.shared.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Rota protegida sem X-Session-Token é 401, não 400: o cliente entende que precisa entrar e os
 * monitores não confundem falta de login com requisição malformada.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MissingSessionHeaderTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void protectedRouteWithoutSessionHeaderRespondsUnauthorized() throws Exception {
		mockMvc.perform(get("/admin/users/suspicious"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

		mockMvc.perform(get("/moderation/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

		// Token inválido também é barrado pelo interceptor de /admin antes do controller.
		mockMvc.perform(get("/admin/users/suspicious").header("X-Session-Token", "invalido"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}
}
