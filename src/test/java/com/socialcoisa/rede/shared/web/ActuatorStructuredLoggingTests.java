package com.socialcoisa.rede.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Regressao do incidente real de prod (2026-10-24): com {@code MANAGEMENT_ENDPOINTS=health,prometheus,metrics}
 * e logging estruturado ECS ligados ao mesmo tempo (exatamente a combinacao do PR #121), o Actuator
 * respondia HTTP 200 com corpo vazio. Causa raiz: {@link RequestObservabilityFilter} escrevia
 * "http.method"/"http.path" tanto via MDC quanto via {@code addKeyValue} na MESMA linha de log do
 * access log; o encoder ECS do Spring Boot (structured logging nativo) lanca
 * {@code IllegalStateException: Duplicate nested pairs added under 'http.method'} quando o mesmo
 * campo aninhado chega duas vezes no mesmo evento — isso corrompia a escrita da resposta no meio do
 * processamento. Este teste liga a mesma combinacao de prod (perfil prd-like: ECS + endpoints extras)
 * e verifica que o Actuator responde com corpo real, nao so HTTP 200.
 */
@SpringBootTest(properties = {
	"springdoc.api-docs.enabled=false",
	"springdoc.swagger-ui.enabled=false",
	"management.endpoints.web.exposure.include=health,prometheus,metrics",
	// Mesmo formato ligado em prd/hml/qa: e o encoder que lancava a exception de campo duplicado.
	"logging.structured.format.console=ecs",
})
@AutoConfigureMockMvc
class ActuatorStructuredLoggingTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void prometheusRespondsWithRealMetricsBodyNotEmpty() throws Exception {
		MvcResult result = mockMvc.perform(get("/actuator/prometheus"))
			.andExpect(status().isOk())
			.andReturn();

		String body = result.getResponse().getContentAsString();
		assertThat(body).isNotBlank();
		// Formato de exposicao do Prometheus: toda metrica real vem com pelo menos um comentario HELP/TYPE.
		assertThat(body).contains("# HELP");
	}

	@Test
	void metricsIndexRespondsWithRealBodyNotEmpty() throws Exception {
		MvcResult result = mockMvc.perform(get("/actuator/metrics")).andExpect(status().isOk()).andReturn();

		assertThat(result.getResponse().getContentAsString()).isNotBlank();
	}

	@Test
	void healthRespondsWithRealBodyNotEmpty() throws Exception {
		MvcResult result = mockMvc.perform(get("/actuator/health")).andExpect(status().isOk()).andReturn();

		assertThat(result.getResponse().getContentAsString()).isNotBlank();
	}
}
