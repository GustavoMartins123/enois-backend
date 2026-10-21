package com.socialcoisa.rede.securityaudit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.socialcoisa.rede.identity.application.port.out.TwoFactorRepository;
import com.socialcoisa.rede.identity.domain.TwoFactorChallenge;
import com.socialcoisa.rede.identity.domain.TwoFactorMethod;
import com.socialcoisa.rede.shared.security.Hashes;
import com.socialcoisa.rede.shared.web.TestAuthHelper;

import tools.jackson.databind.ObjectMapper;

/** Exercises committed authentication state, not just a mock of the attempt counter. */
@SpringBootTest(properties = "rede.moderation.spam.enabled=false")
@AutoConfigureMockMvc
class AuthenticationStateAuditTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired TwoFactorRepository challenges;

    @Test
    void failedTwoFactorLoginAttemptsMustSurviveTheRejectedTransaction() throws Exception {
        var user = new TestAuthHelper(mvc, json).authenticate("audit_2fa_attempts");
        String id = challenge(user.userId());
        for (int i = 0; i < TwoFactorChallenge.MAX_ATTEMPTS; i++) {
            assertThat(verify(id, "000000")).isEqualTo(400);
        }
        assertThat(challenges.findChallenge(id).orElseThrow().attempts())
            .as("O rollback do erro nao pode apagar o contador de tentativas").isEqualTo(5);
        assertThat(verify(id, "000000")).isEqualTo(429);
    }

    @Test
    void failedEmailSetupAttemptsMustAlsoSurviveTheRejectedTransaction() throws Exception {
        var user = new TestAuthHelper(mvc, json).authenticate("audit_2fa_setup_attempts");
        String id = challenge(user.userId());
        for (int i = 0; i < TwoFactorChallenge.MAX_ATTEMPTS; i++) {
            var response = mvc.perform(post("/auth/2fa/email/confirm").header("X-Session-Token", user.token())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("code", "000000"))))
                .andReturn().getResponse();
            assertThat(response.getStatus()).isEqualTo(400);
        }
        assertThat(challenges.findChallenge(id).orElseThrow().attempts())
            .as("Setup de segundo fator tambem precisa persistir erros").isEqualTo(5);
    }

    @Test
    void aTwoFactorChallengeMustOnlyIssueOneSessionUnderConcurrentReplay() throws Exception {
        var user = new TestAuthHelper(mvc, json).authenticate("audit_2fa_replay");
        String id = challenge(user.userId());
        try (var pool = Executors.newFixedThreadPool(6)) {
            var barrier = new CountDownLatch(1);
            var jobs = new ArrayList<Callable<Integer>>();
            for (int i = 0; i < 6; i++) jobs.add(() -> {
                assertThat(barrier.await(10, TimeUnit.SECONDS)).isTrue();
                return verify(id, "123456");
            });
            var results = jobs.stream().map(pool::submit).toList();
            barrier.countDown();
            var statuses = new ArrayList<Integer>();
            for (var result : results) statuses.add(result.get(20, TimeUnit.SECONDS));
            assertThat(statuses).allMatch(status -> status == 200 || status == 409);
            assertThat(statuses.stream().filter(status -> status == 200).count())
                .as("Um desafio de uso unico nao pode gerar varias sessoes concorrentes").isEqualTo(1);
        }
    }

    private String challenge(String userId) {
        Instant now = Instant.now();
        String id = "audit_2fa_" + UUID.randomUUID().toString().replace("-", "");
        challenges.saveChallenge(TwoFactorChallenge.create(id, userId, TwoFactorMethod.EMAIL,
            Hashes.sha256Hex("123456"), now.plusSeconds(600), now));
        return id;
    }
    private int verify(String id, String code) throws Exception {
        return mvc.perform(post("/auth/2fa/verify").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("challengeId", id, "code", code))))
            .andReturn().getResponse().getStatus();
    }
}
