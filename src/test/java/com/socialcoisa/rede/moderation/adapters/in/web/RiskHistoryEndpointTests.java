package com.socialcoisa.rede.moderation.adapters.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.socialcoisa.rede.shared.risk.RiskEngine;
import com.socialcoisa.rede.shared.security.BehavioralTimingGuard;
import com.socialcoisa.rede.shared.web.TestAuthHelper;

import tools.jackson.databind.ObjectMapper;

/**
 * O histórico de risco que a moderação consulta.
 *
 * <p>O motor de risco grava cada avaliação (nível, sinais, versão da regra) justamente para a
 * decisão poder ser reconstruída depois. Este teste fecha o caminho: o admin vê o histórico da
 * conta, e quem não é admin não enxerga.
 */
@SpringBootTest
@TestPropertySource(properties = { "rede.admin.emails=mp_risco@test.com" })
class RiskHistoryEndpointTests {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private RiskEngine riskEngine;

	@Autowired
	private BehavioralTimingGuard timingGuard;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
	}

	@Test
	void oAdminVeOHistoricoDeRiscoDaConta() throws Exception {
		var admin = new TestAuthHelper(mockMvc, objectMapper).authenticate("mp_risco");
		var alvo = new TestAuthHelper(mockMvc, objectMapper).authenticate("mp_alvo");
		// O @username vem do slug do nome (mp_alvo vira mp-alvo): le do /me, como o painel faz.
		String alvoUsername = objectMapper.readTree(mockMvc
			.perform(get("/me").header("X-Session-Token", alvo.token()))
			.andReturn().getResponse().getContentAsString()).get("username").asText();

		// Uma conta com cadencia de robo: o motor avalia e escala.
		for (int i = 0; i < 12; i++) {
			timingGuard.record(alvo.userId());
		}
		riskEngine.assess(alvo.userId(), "publish", "conteudo");

		mockMvc.perform(get("/admin/users/" + alvoUsername + "/risco").header("X-Session-Token", admin.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].userId").value(alvo.userId()))
			.andExpect(jsonPath("$[0].level").value("MEDIUM"))
			.andExpect(jsonPath("$[0].signals[0]").value("ROBOTIC_CADENCE"))
			.andExpect(jsonPath("$[0].ruleVersion").isNotEmpty());
	}

	@Test
	void quemNaoEAdminNaoEnxergaORisco() throws Exception {
		var comum = new TestAuthHelper(mockMvc, objectMapper).authenticate("mp_comum");

		mockMvc.perform(get("/admin/users/mp-alvo/risco").header("X-Session-Token", comum.token()))
			.andExpect(status().isForbidden());
	}
}
