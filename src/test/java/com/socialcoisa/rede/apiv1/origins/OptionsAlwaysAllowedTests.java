package com.socialcoisa.rede.apiv1.origins;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Preflight responde sempre — inclusive de origem não cadastrada e em caminho que não existe.
 *
 * <p>O OPTIONS não executa nada nem devolve dado; a operação real continua barrada pelo gate de
 * origem. Recusar o preflight não protege nada e só troca a recusa por uma falha de CORS no
 * navegador, que aparece para a pessoa como "sem conexão" numa ação que nunca chegou ao servidor.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
	"rede.origins.enforce=true",
	"rede.cors.allowed-origins=https://enois.ia.br"
})
class OptionsAlwaysAllowedTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void unknownOriginGetsPreflightAnswered() throws Exception {
		mockMvc.perform(options("/feed/posts/x/reactions")
				.header("Origin", "https://exemplo-nao-cadastrado.test")
				.header("Access-Control-Request-Method", "POST")
				.header("Access-Control-Request-Headers", "content-type,x-session-token,idempotency-key"))
			.andExpect(status().is2xxSuccessful())
			.andExpect(header().string("Access-Control-Allow-Origin", "https://exemplo-nao-cadastrado.test"))
			.andExpect(header().string("Access-Control-Allow-Methods",
				org.hamcrest.Matchers.containsString("OPTIONS")))
			.andExpect(header().string("Access-Control-Allow-Headers",
				org.hamcrest.Matchers.containsString("x-session-token")));
	}

	@Test
	void pathThatDoesNotExistAlsoGetsPreflightAnswered() throws Exception {
		mockMvc.perform(options("/caminho/que/nao/existe")
				.header("Origin", "https://enois.ia.br")
				.header("Access-Control-Request-Method", "POST"))
			.andExpect(status().is2xxSuccessful());
	}

	@Test
	void preflightWithoutOriginIsNotRejected() throws Exception {
		mockMvc.perform(options("/feed/posts/x/reactions")
				.header("Access-Control-Request-Method", "POST"))
			.andExpect(status().is2xxSuccessful());
	}
}
