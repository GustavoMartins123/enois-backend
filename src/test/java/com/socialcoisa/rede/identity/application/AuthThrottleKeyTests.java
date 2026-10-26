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

	@Test
	void sprayingOneAccountFromManyIpsAddsACappedDelayButNeverALock() {
		AuthThrottle throttle = new AuthThrottle(5, 10);
		// 19 failures, each from a different network: under the account-wide threshold (4 x 5).
		for (int i = 0; i < 19; i++) {
			throttle.registraFalhaLogin("sprayed@test.com", "198.51.100." + i);
		}
		assertThat(throttle.loginDelayMillis("sprayed@test.com", "203.0.113.7")).isZero();

		for (int i = 19; i < 500; i++) {
			throttle.registraFalhaLogin("sprayed@test.com", "198.51." + (i / 250) + "." + (i % 250));
		}
		long ownerDelay = throttle.loginDelayMillis("sprayed@test.com", "203.0.113.7");
		assertThat(ownerDelay).isPositive().isLessThanOrEqualTo(1_000L);
		// Another account is untouched.
		assertThat(throttle.loginDelayMillis("other@test.com", "203.0.113.7")).isZero();

		throttle.limpaLogin("sprayed@test.com", "203.0.113.7");
		assertThat(throttle.loginDelayMillis("sprayed@test.com", "203.0.113.7")).isZero();
	}

	@Test
	void accountAndIpDelayStillAppliesOnItsOwn() {
		AuthThrottle throttle = new AuthThrottle(5, 10);
		for (int i = 0; i < 5; i++) {
			throttle.registraFalhaLogin("target@test.com", "198.51.100.1");
		}
		assertThat(throttle.loginDelayMillis("target@test.com", "198.51.100.1")).isPositive();
		assertThat(throttle.loginDelayMillis("target@test.com", "203.0.113.7")).isZero();
	}
}
