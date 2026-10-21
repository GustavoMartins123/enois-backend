package com.socialcoisa.rede.securityaudit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.socialcoisa.rede.shared.web.TestAuthHelper;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "resilience4j.ratelimiter.instances.auth.limit-for-period=20")
class SharedAuthenticationQuotaAuditTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RateLimiterRegistry limiters;

    @Test
    void oneClientsInvalidLoginsMustNotDisableAnotherClientsValidLogin() throws Exception {
        new TestAuthHelper(mvc, json).authenticate("audit_auth_quota_victim");
        RateLimiter original = limiters.rateLimiter("auth");
        // Start a fresh window with the same production budget (20/min), without sleeping.
        limiters.replace("auth", RateLimiter.of("auth", RateLimiterConfig.custom()
            .limitForPeriod(20).limitRefreshPeriod(Duration.ofMinutes(1)).timeoutDuration(Duration.ZERO).build()));
        try {
            for (int i = 0; i < 20; i++) {
                var response = mvc.perform(post("/auth/login").header("X-Forwarded-For", "192.0.2.10")
                    .header("Idempotency-Key", "quota-attack-" + i).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("email", "audit_nonexistent_" + i + "@example.invalid",
                        "password", "wrong-audit-password")))).andReturn().getResponse();
                assertThat(response.getStatus()).isIn(400, 401);
            }
            var victim = mvc.perform(post("/auth/login").header("X-Forwarded-For", "198.51.100.20")
                .header("Idempotency-Key", "quota-victim-login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", "audit_auth_quota_victim@test.com",
                    "password", "Test1234!")))).andReturn().getResponse();
            assertThat(victim.getStatus()).as("Outro IP e credenciais corretas nao podem perder o login por cota global: %s",
                victim.getContentAsString()).isEqualTo(200);
        } finally {
            limiters.replace("auth", original);
        }
    }
}
