package com.socialcoisa.rede.securityaudit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.socialcoisa.rede.profile.application.ReputationService;
import com.socialcoisa.rede.shared.web.TestAuthHelper;

import tools.jackson.databind.ObjectMapper;

/**
 * The DM that accompanies a job application is best-effort: when the contact rules refuse it, the
 * application and its triage still have to be saved (no silent rollback turning into a 500).
 */
@SpringBootTest(properties = "rede.moderation.spam.enabled=false")
@AutoConfigureMockMvc
class JobApplicationContactAuditTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @MockitoBean ReputationService reputationService;

    @BeforeEach
    void reputation() {
        when(reputationService.reputationOf(anyString())).thenReturn(300);
    }

    @Test
    void employerTriagesAMinorsApplicationWithoutDirectMessagingTheMinor() throws Exception {
        var employer = auth("audit_job_minor_owner");
        var candidate = auth("audit_job_minor_candidate");
        String jobId = createJob(employer);
        String applicationId = apply(candidate, jobId);
        db.update("update users set birth_date = ? where id = ?",
            LocalDate.now(ZoneId.of("America/Sao_Paulo")).minusYears(16).toString(), candidate.userId());

        var moved = call(employer, "PATCH", "/jobs/" + jobId + "/applications/" + applicationId,
            Map.of("status", "IN_REVIEW")).getResponse();

        assertThat(moved.getStatus()).isEqualTo(200);
        assertThat(db.queryForObject("select status from job_applications where id = ?", String.class,
            applicationId)).isEqualTo("IN_REVIEW");
        assertThat(messagesBetween(employer.userId(), candidate.userId()))
            .as("Sem conexao aceita, o empregador nao manda DM ao menor").isZero();
    }

    @Test
    void blockedCandidateCanStillApplyButNoDirectMessageIsDelivered() throws Exception {
        var employer = auth("audit_job_block_owner");
        var candidate = auth("audit_job_block_candidate");
        String jobId = createJob(employer);
        assertThat(call(employer, "POST", "/network/blocks/" + candidate.userId(), null)
            .getResponse().getStatus()).isEqualTo(201);

        var applied = call(candidate, "POST", "/jobs/" + jobId + "/applications",
            Map.of("message", "Tenho interesse.")).getResponse();

        assertThat(applied.getStatus()).isEqualTo(201);
        assertThat(db.queryForObject("select count(*) from job_applications where job_id = ?", Integer.class,
            jobId)).isEqualTo(1);
        assertThat(messagesBetween(candidate.userId(), employer.userId()))
            .as("O bloqueio impede a DM da candidatura").isZero();
    }

    private String createJob(TestAuthHelper.AuthSession owner) throws Exception {
        var result = call(owner, "POST", "/jobs", Map.of(
            "title", "Vaga de auditoria", "company", "Acme", "location", "Remoto",
            "type", "CLT", "level", "PLENO", "skills", List.of(),
            "description", "Vaga de teste com valor.", "salaryMin", "1500",
            "communityAgreement", true)).getResponse();
        assertThat(result.getStatus()).isEqualTo(201);
        return json.readTree(result.getContentAsString()).get("id").asText();
    }

    private String apply(TestAuthHelper.AuthSession candidate, String jobId) throws Exception {
        var result = call(candidate, "POST", "/jobs/" + jobId + "/applications",
            Map.of("message", "Tenho interesse.")).getResponse();
        assertThat(result.getStatus()).isEqualTo(201);
        return json.readTree(result.getContentAsString()).get("id").asText();
    }

    private int messagesBetween(String senderId, String recipientId) {
        return db.queryForObject("""
            select count(*) from messages m
            join conversation_participants p on p.conversation_id = m.conversation_id and p.user_id = ?
            where m.sender_id = ?""", Integer.class, recipientId, senderId);
    }

    private TestAuthHelper.AuthSession auth(String name) throws Exception {
        return new TestAuthHelper(mvc, json).authenticate(name);
    }

    private MvcResult call(TestAuthHelper.AuthSession session, String method, String path, Object body) throws Exception {
        var request = MockMvcRequestBuilders.request(HttpMethod.valueOf(method), path)
            .header("X-Session-Token", session.token())
            .header("Idempotency-Key", "audit-" + java.util.UUID.randomUUID());
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(request).andReturn();
    }
}
