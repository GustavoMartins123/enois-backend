package com.socialcoisa.rede.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Failed logins slow down only the network that produced them, never the account owner elsewhere. */
class AuthThrottleKeyTests {

	@Test
	void failuresFromOneIpDoNotDelayAnotherIp() {
		AuthThrottle throttle = new AuthThrottle(1, 10);
		for (int i = 0; i < 3; i++) {
			throttle.registraFalhaLogin("victim@test.com", "198.51.100.1");
		}

		long start = System.nanoTime();
		throttle.exigeLoginPermitido("victim@test.com", "203.0.113.7");
		long otherIpMillis = (System.nanoTime() - start) / 1_000_000;

		start = System.nanoTime();
		throttle.exigeLoginPermitido("victim@test.com", "198.51.100.1");
		long sameIpMillis = (System.nanoTime() - start) / 1_000_000;

		assertThat(otherIpMillis).isLessThan(100);
		assertThat(sameIpMillis).isGreaterThanOrEqualTo(200);
	}
}
