package com.socialcoisa.rede.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.socialcoisa.rede.shared.security.ClientIpResolver;

/** Duplo clique concorrente: o segundo POST espera e recebe a resposta do primeiro. */
class DuplicatePostFilterTests {

	@Test
	void concurrentIdenticalPostReplaysTheFirstResponse() throws Exception {
		var filter = new DuplicatePostFilter(new ClientIpResolver(), true);
		AtomicInteger executions = new AtomicInteger();
		CountDownLatch started = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);

		MockHttpServletResponse first = new MockHttpServletResponse();
		Thread firstCall = new Thread(() -> {
			try {
				filter.doFilter(request(), first, (req, res) -> {
					executions.incrementAndGet();
					started.countDown();
					release.await(3, TimeUnit.SECONDS);
					var http = (jakarta.servlet.http.HttpServletResponse) res;
					http.setStatus(201);
					http.setContentType("application/json");
					http.getWriter().write("{\"id\":\"msg_1\"}");
				});
			}
			catch (Exception ignored) {
			}
		});
		firstCall.start();
		assertThat(started.await(3, TimeUnit.SECONDS)).isTrue();

		MockHttpServletResponse second = new MockHttpServletResponse();
		var replay = Executors.newSingleThreadExecutor();
		var secondCall = replay.submit(() -> {
			filter.doFilter(request(), second, (req, res) -> executions.incrementAndGet());
			return second;
		});
		Thread.sleep(150);
		release.countDown();
		firstCall.join(3000);
		secondCall.get(3, TimeUnit.SECONDS);

		assertThat(executions.get()).isEqualTo(1);
		assertThat(second.getStatus()).isEqualTo(201);
		assertThat(second.getContentAsString()).isEqualTo("{\"id\":\"msg_1\"}");
		replay.shutdownNow();
	}

	private MockHttpServletRequest request() {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/conversations/conv_1/messages");
		request.setContentType("application/json");
		request.setContent("{\"content\":\"oi\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
		request.addHeader("X-Session-Token", "sessao-de-teste");
		return request;
	}
}
