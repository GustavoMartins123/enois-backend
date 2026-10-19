package com.socialcoisa.rede.linkedin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.socialcoisa.rede.linkedin.application.LinkedInIntegrationService;
import com.socialcoisa.rede.shared.web.TestAuthHelper;

import tools.jackson.databind.ObjectMapper;

/**
 * A API da integracao de publicacao no LinkedIn.
 *
 * O que este teste tranca: sem sessao nao le; sem conexao nao liga a preferencia; com a conexao
 * gravada (token cifrado pela chave de teste) o status mostra canPost e a preferencia liga/desliga;
 * a conexao de uma pessoa nao aparece para outra.
 */
@SpringBootTest(properties = "rede.security.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@AutoConfigureMockMvc
class LinkedInIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private LinkedInIntegrationService linkedin;

	private TestAuthHelper authHelper() {
		return new TestAuthHelper(mockMvc, objectMapper);
	}

	private void setPostSync(TestAuthHelper.AuthSession user, boolean enabled) throws Exception {
		mockMvc.perform(put("/me/integrations/linkedin")
				.header("X-Session-Token", user.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("postSync", enabled))))
			.andExpect(status().isOk());
	}

	@Test
	void semSessaoNaoLeNemMuda() throws Exception {
		mockMvc.perform(get("/me/integrations/linkedin"))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(put("/me/integrations/linkedin")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"postSync\":true}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void semConexaoNaoLigaAPreferencia() throws Exception {
		var user = authHelper().authenticate("li-sync-sem-conexao");

		mockMvc.perform(get("/me/integrations/linkedin")
				.header("X-Session-Token", user.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.connected").value(false))
			.andExpect(jsonPath("$.canPost").value(false))
			.andExpect(jsonPath("$.reason").value("NOT_CONNECTED"));

		mockMvc.perform(put("/me/integrations/linkedin")
				.header("X-Session-Token", user.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"postSync\":true}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("LINKEDIN_NOT_CONNECTED"));
	}

	@Test
	void conexaoGravadaPermiteLigarEDesligar() throws Exception {
		var user = authHelper().authenticate("li-sync-com-conexao");
		linkedin.remember(user.userId(), "sub-int", "token-int", Instant.now().plusSeconds(3600));

		mockMvc.perform(get("/me/integrations/linkedin")
				.header("X-Session-Token", user.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.connected").value(true))
			.andExpect(jsonPath("$.canPost").value(true))
			.andExpect(jsonPath("$.postSync").value(false));

		setPostSync(user, true);

		mockMvc.perform(get("/me/integrations/linkedin")
				.header("X-Session-Token", user.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.postSync").value(true));

		setPostSync(user, false);
	}

	@Test
	void aConexaoDeUmaPessoaNaoApareceParaOutra() throws Exception {
		var dona = authHelper().authenticate("li-sync-dona");
		var outra = authHelper().authenticate("li-sync-outra");
		linkedin.remember(dona.userId(), "sub-dona", "token-dona", Instant.now().plusSeconds(3600));

		mockMvc.perform(get("/me/integrations/linkedin")
				.header("X-Session-Token", outra.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.connected").value(false))
			.andExpect(jsonPath("$.reason").value("NOT_CONNECTED"));

		assertThat(dona.userId()).isNotEqualTo(outra.userId());
	}
}
