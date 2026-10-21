package com.socialcoisa.rede.securityaudit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.socialcoisa.rede.shared.web.TestAuthHelper;

import tools.jackson.databind.ObjectMapper;

/** Real publication HTTP flows with throttling and dedup enabled; entirely synthetic accounts. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "rede.rate.post-limit=10", "rede.rate.post-window-minutes=60",
    "rede.rate.duplicate-window-minutes=60", "rede.rate.post-guard-enabled=true",
    "rede.rate.post-user-per-minute=1000", "rede.rate.post-ip-per-minute=10000"
})
class PublishingAbuseAuditTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;

    @Test
    void burstStopsAtTheProductionPostLimitAndDoesNotConsumeAnotherUsersQuota() throws Exception {
        var attacker = auth("audit_burst_attacker");
        var victim = auth("audit_burst_victim");
        for (int i = 0; i < 24; i++) {
            var response = publish(attacker, "burst-" + i, Map.of("type", "INSIGHT", "content", "burst item " + i));
            assertThat(response.getResponse().getStatus()).as("burst request %s", i).isEqualTo(i < 10 ? 201 : 429);
        }
        assertThat(countPosts(attacker.userId())).isEqualTo(10);
        assertThat(publish(victim, "victim-allowed", Map.of("type", "INSIGHT", "content", "publicacao legitima"))
            .getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void aNewSessionAndSpoofedUserHeaderDoNotResetTheAuthorsPublicationLimit() throws Exception {
        var attacker = auth("audit_multisession");
        var victim = auth("audit_multisession_victim");
        for (int i = 0; i < 10; i++) {
            assertThat(publish(attacker, "session-initial-" + i,
                Map.of("type", "INSIGHT", "content", "sessao inicial item " + i))
                .getResponse().getStatus()).isEqualTo(201);
        }
        String loginBody = mvc.perform(post("/auth/login").header("Idempotency-Key", "audit-fresh-login")
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                "email", "audit_multisession@test.com", "password", "Test1234!"))))
            .andReturn().getResponse().getContentAsString();
        String freshToken = json.readTree(loginBody).get("token").asText();
        assertThat(freshToken).isNotEqualTo(attacker.token());
        assertThat(mvc.perform(post("/feed/posts").header("X-Session-Token", freshToken)
            .header("X-User-Id", victim.userId()).header("Idempotency-Key", "session-bypass")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("type", "INSIGHT", "content", "nova sessao mesmo autor"))))
            .andReturn().getResponse().getStatus()).isEqualTo(429);
        assertThat(countPosts(victim.userId())).isZero();
        assertThat(countPosts(attacker.userId())).isEqualTo(10);
    }

    @Test
    void concurrentRequestsCannotExceedThePublicationQuota() throws Exception {
        var attacker = auth("audit_parallel_publish");
        try (var pool = Executors.newFixedThreadPool(8)) {
            var jobs = new ArrayList<Callable<Integer>>();
            for (int i = 0; i < 48; i++) {
                int item = i;
                jobs.add(() -> publish(attacker, "parallel-" + item,
                    Map.of("type", "INSIGHT", "content", "parallel publication " + item))
                    .getResponse().getStatus());
            }
            var statuses = new ArrayList<Integer>();
            for (var result : pool.invokeAll(jobs, 30, TimeUnit.SECONDS)) statuses.add(result.get());
            assertThat(statuses).containsOnly(201, 429);
            assertThat(statuses.stream().filter(status -> status == 201).count()).isEqualTo(10);
            assertThat(countPosts(attacker.userId())).isEqualTo(10);
        }
    }

    @Test
    void replayingTheSameCommandDoesNotDuplicatePostsOrConsumePublicationQuota() throws Exception {
        var user = auth("audit_replay_publish");
        var body = Map.of("type", "INSIGHT", "content", "uma publicacao repetida por retry");
        String first = publish(user, "same-command", body).getResponse().getContentAsString();
        for (int i = 0; i < 12; i++) {
            var replay = publish(user, "same-command", body).getResponse();
            assertThat(replay.getStatus()).isEqualTo(201);
            assertThat(replay.getContentAsString()).isEqualTo(first);
        }
        assertThat(countPosts(user.userId())).isEqualTo(1);
        assertThat(publish(user, "after-retry", Map.of("type", "INSIGHT", "content", "retry nao esgota cota"))
            .getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void duplicateTextIsRejectedPerAuthorWithCaseAndWhitespaceNormalization() throws Exception {
        var attacker = auth("audit_duplicate_publish");
        var other = auth("audit_duplicate_other");
        assertThat(publish(attacker, "dedup-first", Map.of("type", "INSIGHT", "content", "Conteudo repetido aqui"))
            .getResponse().getStatus()).isEqualTo(201);
        var rejected = publish(attacker, "dedup-second",
            Map.of("type", "INSIGHT", "content", "  CONTEUDO   repetido aqui  ")).getResponse();
        assertThat(rejected.getStatus()).isEqualTo(409);
        assertThat(json.readTree(rejected.getContentAsString()).get("code").asText()).isEqualTo("DUPLICATE_CONTENT");
        assertThat(publish(other, "dedup-other", Map.of("type", "INSIGHT", "content", "Conteudo repetido aqui"))
            .getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void aRejectedArticleMustNotPreventItsAuthorFromCorrectingAndPublishingIt() throws Exception {
        var user = auth("audit_failed_article");
        var rejected = publish(user, "article-invalid", Map.of("type", "ARTICLE", "content", "Titulo que sera corrigido"));
        assertThat(rejected.getResponse().getStatus()).isEqualTo(400);
        assertThat(countPosts(user.userId())).isZero();
        var corrected = publish(user, "article-corrected", Map.of("type", "ARTICLE",
            "content", "Titulo que sera corrigido", "articleBody", "Agora o corpo do artigo esta preenchido."));
        assertThat(corrected.getResponse().getStatus()).as("A tentativa recusada nao deve reservar o texto: %s",
            corrected.getResponse().getContentAsString()).isEqualTo(201);
    }

    @Test
    void duplicatePagesMustNotBypassDedupByOmittingTheOptionalTitle() throws Exception {
        var user = auth("audit_duplicate_page");
        var body = Map.of("type", "PAGE", "pageHtml", "<p>pagina repetida sem titulo</p>");
        assertThat(publish(user, "page-duplicate-1", body).getResponse().getStatus()).isEqualTo(201);
        var repeated = publish(user, "page-duplicate-2", body).getResponse();
        assertThat(repeated.getStatus()).as("O mesmo HTML de pagina nao deve escapar da deduplicacao")
            .isEqualTo(409);
    }

    @Test
    void aCommentRolledBackAfterDedupMustAllowACorrectedTarget() throws Exception {
        var user = auth("audit_comment_rollback");
        var body = Map.of("content", "Comentario repetido apos corrigir o destino");
        var rejected = mvc.perform(post("/feed/posts/missing-audit-post/comments")
            .header("X-Session-Token", user.token()).header("Idempotency-Key", "comment-invalid-target")
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
            .andReturn().getResponse();
        assertThat(rejected.getStatus()).isEqualTo(404);
        var target = publish(user, "comment-valid-post", Map.of("type", "INSIGHT", "content", "Destino valido do comentario"))
            .getResponse();
        assertThat(target.getStatus()).isEqualTo(201);
        String postId = json.readTree(target.getContentAsString()).get("id").asText();
        var corrected = mvc.perform(post("/feed/posts/" + postId + "/comments")
            .header("X-Session-Token", user.token()).header("Idempotency-Key", "comment-corrected-target")
            .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
            .andReturn().getResponse();
        assertThat(corrected.getStatus()).as(corrected.getContentAsString()).isEqualTo(201);
    }

    private TestAuthHelper.AuthSession auth(String name) throws Exception {
        return new TestAuthHelper(mvc, json).authenticate(name);
    }
    private MvcResult publish(TestAuthHelper.AuthSession session, String key, Object body) throws Exception {
        return mvc.perform(post("/feed/posts").header("X-Session-Token", session.token())
            .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(body))).andReturn();
    }
    private int countPosts(String userId) {
        return db.queryForObject("select count(*) from posts where author_id = ?", Integer.class, userId);
    }
}
