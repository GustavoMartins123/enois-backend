package com.socialcoisa.rede.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** O proxy (Traefik) poe o IP real do cliente como primeiro item do X-Forwarded-For. */
class ClientIpSpoofingTests {

	private static final String REAL_IP = "203.0.113.7";

	private final ClientIpResolver resolver = new ClientIpResolver();

	@Test
	void usesTheHopTheLoadBalancerAppendedAndIgnoresClientHeaders() {
		MockHttpServletRequest request = albRequest();
		request.addHeader("X-Real-IP", "6.6.6.6");

		assertThat(resolver.resolve(request)).isEqualTo("9.9.9.9");
	}

	@Test
	void paddedForwardedForDoesNotFallBackToTheClientsRealIpHeader() {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/signup");
		request.addHeader("X-Forwarded-For", "1.1.1.1, ".repeat(100) + REAL_IP);
		request.addHeader("X-Real-IP", "6.6.6.6");

		assertThat(resolver.resolve(request)).isEqualTo("1.1.1.1");
	}

	@Test
	void implausibleLastHopFallsToTheConnectionNotToTheClientsRealIpHeader() {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/feed");
		request.addHeader("X-Forwarded-For", "not-an-ip");
		request.addHeader("X-Real-IP", "6.6.6.6");
		request.setRemoteAddr("10.0.0.5");

		assertThat(resolver.resolve(request)).isEqualTo("10.0.0.5");
	}

	@Test
	void rotatingRealIpHeaderDoesNotEscapeTheGlobalLimit() throws Exception {
		Clock clock = Clock.fixed(Instant.parse("2026-10-20T00:00:00Z"), ZoneOffset.UTC);
		GlobalApiRateLimitFilter filter = new GlobalApiRateLimitFilter(clock, resolver, 3, 50);

		int lastStatus = 0;
		for (int i = 0; i < 4; i++) {
			MockHttpServletRequest request = albRequest();
			request.addHeader("X-Real-IP", "6.6.6." + i);
			MockHttpServletResponse response = new MockHttpServletResponse();
			filter.doFilter(request, response, new MockFilterChain());
			lastStatus = response.getStatus();
		}

		assertThat(lastStatus).isEqualTo(429);
	}

	/** POST: o teto global agora vale para escrita (leitura é livre). */
	private static MockHttpServletRequest albRequest() {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/feed");
		request.addHeader("X-Forwarded-For", "9.9.9.9, " + REAL_IP);
		return request;
	}
}
