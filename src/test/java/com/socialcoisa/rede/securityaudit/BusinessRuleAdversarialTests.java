package com.socialcoisa.rede.securityaudit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.socialcoisa.rede.moderation.application.port.out.SanctionRepository;
import com.socialcoisa.rede.moderation.domain.UserSanction;
import com.socialcoisa.rede.shared.web.TestAuthHelper;

import tools.jackson.databind.ObjectMapper;

/** Assertions state the secure behavior. A failing assertion is a reproducible finding, not a waiver. */
@SpringBootTest(properties = {"rede.admin.usernames=audit-reserved-admin", "rede.moderation.spam.enabled=false"})
@AutoConfigureMockMvc
@org.springframework.test.context.event.RecordApplicationEvents
class BusinessRuleAdversarialTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired SanctionRepository sanctions;
    @Autowired org.springframework.test.context.event.ApplicationEvents events;

    @Test
    void renamingYourOwnProfileMustNotGrantAdministratorPrivileges() throws Exception {
        var user = auth("audit_admin_rename");
        assertThat(call(user, "GET", "/admin/blocked-ips", null).getResponse().getStatus()).isEqualTo(403);
        try {
            var renamed = call(user, "PATCH", "/me/profile", Map.of("username", "audit-reserved-admin"));
            assertThat(renamed.getResponse().getStatus()).isIn(200, 400, 403);
            assertThat(call(user, "GET", "/admin/blocked-ips", null).getResponse().getStatus())
                .as("Um campo editavel do perfil nao pode conceder privilegio administrativo").isEqualTo(403);
        } finally {
            db.update("update users set username = ? where id = ?", "audit-admin-rename", user.userId());
        }
    }

    @Test
    void choosingAnAdministratorUsernameDuringSignupMustNotGrantAdministratorPrivileges() throws Exception {
        var user = auth("audit-reserved-admin");
        try {
            assertThat(call(user, "GET", "/admin/blocked-ips", null).getResponse().getStatus())
                .as("Uma conta nova nao pode assumir uma identidade administrativa apenas pelo nome")
                .isEqualTo(403);
        } finally {
            db.update("update users set username = ? where id = ?", "audit-canary-retired", user.userId());
        }
    }

    @Test
    void anOutsiderCannotReadOrSendMessagesEvenWithAForgedParticipantHeader() throws Exception {
        var first = auth("audit_idor_chat_a");
        var second = auth("audit_idor_chat_b");
        var attacker = auth("audit_idor_chat_attacker");
        String conversation = direct(first, second);
        for (String suffix : List.of("", "/messages")) {
            assertThat(mvc.perform(get("/conversations/" + conversation + suffix)
                .header("X-Session-Token", attacker.token()).header("X-User-Id", first.userId()))
                .andReturn().getResponse().getStatus()).isEqualTo(403);
        }
        assertThat(call(attacker, "POST", "/conversations/" + conversation + "/messages",
            Map.of("text", "mensagem intrusa")).getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void blockingSomeoneMustStopMessagesInAnAlreadyExistingConversation() throws Exception {
        var attacker = auth("audit_existing_dm_attacker");
        var victim = auth("audit_existing_dm_victim");
        String conversation = direct(attacker, victim);
        block(victim, attacker);
        var response = call(attacker, "POST", "/conversations/" + conversation + "/messages",
            Map.of("text", "Contato indesejado depois do bloqueio"));
        assertThat(response.getResponse().getStatus()).as("Enviar na conversa antiga deve respeitar bloqueio")
            .isEqualTo(403);
    }

    @Test
    void genericConversationCreationMustNotBypassTheDirectConversationBlock() throws Exception {
        var attacker = auth("audit_alternate_dm_attacker");
        var victim = auth("audit_alternate_dm_victim");
        block(victim, attacker);
        assertThat(call(attacker, "POST", "/conversations/direct", Map.of("userId", victim.userId()))
            .getResponse().getStatus()).isEqualTo(403);
        var alternate = call(attacker, "POST", "/conversations",
            Map.of("kind", "DIRECT", "title", "conversa alternativa", "participantIds", List.of(victim.userId())));
        assertThat(alternate.getResponse().getStatus()).as("Criacao generica deve impor o mesmo bloqueio")
            .isEqualTo(403);
    }

    @Test
    void choosingGroupKindMustNotLetAStrangerForceContactWithAMinor() throws Exception {
        var attacker = auth("audit_minor_dm_adult");
        var minor = auth("audit_minor_dm_child");
        db.update("update users set birth_date = ? where id = ?", LocalDate.now().minusYears(15), minor.userId());
        String direct = direct(attacker, minor);
        assertThat(call(attacker, "POST", "/conversations/" + direct + "/messages",
            Map.of("text", "teste de contato direto")).getResponse().getStatus()).isEqualTo(403);
        var group = call(attacker, "POST", "/conversations", Map.of("kind", "GROUP", "title", "grupo imposto",
            "participantIds", List.of(minor.userId())));
        if (group.getResponse().getStatus() == 403) return;
        assertThat(group.getResponse().getStatus()).isEqualTo(201);
        String id = json.readTree(group.getResponse().getContentAsString()).get("id").asText();
        assertThat(call(attacker, "POST", "/conversations/" + id + "/messages",
            Map.of("text", "teste de contato por grupo imposto")).getResponse().getStatus())
            .as("O destinatario menor nao escolheu nem aceitou participar deste grupo").isEqualTo(403);
    }

    @Test
    void blockedAccountsMustNotWriteCommentsOnTheVictimsPosts() throws Exception {
        var attacker = auth("audit_blocked_comment_attacker");
        var victim = auth("audit_blocked_comment_victim");
        String postId = publish(victim, "Publicacao da vitima de assedio");
        block(victim, attacker);
        var response = call(attacker, "POST", "/feed/posts/" + postId + "/comments",
            Map.of("content", "Comentario depois do bloqueio"));
        assertThat(response.getResponse().getStatus()).as("Saber o ID nao autoriza contatar quem bloqueou")
            .isIn(403, 404);
    }

    @Test
    void blockedMentionsMustNotCreateNotificationsOrPushEventsForTheVictim() throws Exception {
        var attacker = auth("audit_blocked_mention_attacker");
        var victim = auth("audit_blocked_mention_victim");
        block(victim, attacker);
        int before = notificationCount(victim.userId(), attacker.userId());
        var response = call(attacker, "POST", "/feed/posts", Map.of("type", "INSIGHT",
            "content", "Publicacao mencionando quem bloqueou", "mentionedUserIds", List.of(victim.userId())));
        assertThat(response.getResponse().getStatus()).isEqualTo(201);
        assertThat(notificationCount(victim.userId(), attacker.userId()))
            .as("Filtrar somente a leitura nao impede gravacao ou envio de push").isEqualTo(before);
        assertThat(events.stream(com.socialcoisa.rede.notifications.application.NotificationCreatedEvent.class)
            .filter(event -> event.userId().equals(victim.userId()) && event.kind().equals("MENTION")))
            .as("O evento que dispara push tambem deve ser suprimido").isEmpty();
    }

    @Test
    void usersCannotEditOrDeleteAnotherAuthorsPosts() throws Exception {
        var victim = auth("audit_post_owner");
        var attacker = auth("audit_post_outsider");
        String id = publish(victim, "Conteudo original da pessoa dona");
        assertThat(call(attacker, "PATCH", "/feed/posts/" + id, Map.of("content", "conteudo alterado"))
            .getResponse().getStatus()).isEqualTo(403);
        assertThat(call(attacker, "DELETE", "/feed/posts/" + id, null).getResponse().getStatus()).isEqualTo(403);
        assertThat(call(victim, "GET", "/feed/posts/" + id, null).getResponse().getContentAsString())
            .contains("Conteudo original da pessoa dona");
    }

    @Test
    void restrictedAuthorsMustNotReplaceTheirPublicPostThroughTheEditRoute() throws Exception {
        var user = auth("audit_restricted_edit");
        String id = publish(user, "Texto antes da restricao");
        // Seed the restriction itself so notification delivery cannot hide an edit-authorization bug.
        sanctions.save(UserSanction.create("audit_restricted_edit_sanction", user.userId(), "audit-restriction",
            "Restricao sintetica da auditoria", Instant.now()));
        assertThat(call(user, "POST", "/feed/posts", Map.of("type", "INSIGHT", "content", "novo texto restrito"))
            .getResponse().getStatus()).isEqualTo(403);
        assertThat(call(user, "PATCH", "/feed/posts/" + id, Map.of("content", "texto publicado durante restricao"))
            .getResponse().getStatus()).as("Editar nao deve contornar a proibicao de publicar conteudo")
            .isEqualTo(403);
    }

    @Test
    void ordinaryUsersCannotChangeTheExchangeRateOrIssueCurrencyPrizes() throws Exception {
        var user = auth("audit_currency_admin");
        assertThat(call(user, "POST", "/coin/cotacao", Map.of("brlCents", 1, "note", "auditoria"))
            .getResponse().getStatus()).isEqualTo(403);
        assertThat(call(user, "POST", "/carteira/premios", Map.of("toUsername", "audit-currency-admin",
            "amount", 1_000_000, "ref", "audit-prize")).getResponse().getStatus()).isEqualTo(403);
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
    private String direct(TestAuthHelper.AuthSession first, TestAuthHelper.AuthSession second) throws Exception {
        var result = call(first, "POST", "/conversations/direct", Map.of("userId", second.userId())).getResponse();
        assertThat(result.getStatus()).isEqualTo(200);
        return json.readTree(result.getContentAsString()).get("id").asText();
    }
    private void block(TestAuthHelper.AuthSession victim, TestAuthHelper.AuthSession attacker) throws Exception {
        assertThat(call(victim, "POST", "/network/blocks/" + attacker.userId(), null)
            .getResponse().getStatus()).isEqualTo(201);
    }
    private String publish(TestAuthHelper.AuthSession user, String content) throws Exception {
        var result = call(user, "POST", "/feed/posts", Map.of("type", "INSIGHT", "content", content)).getResponse();
        assertThat(result.getStatus()).isEqualTo(201);
        return json.readTree(result.getContentAsString()).get("id").asText();
    }
    private int notificationCount(String victimId, String actorId) {
        return db.queryForObject("select count(*) from notifications where user_id = ? and actor_id = ?",
            Integer.class, victimId, actorId);
    }
}
