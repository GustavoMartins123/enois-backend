package com.socialcoisa.rede.securityaudit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.socialcoisa.rede.shared.web.TestAuthHelper;
import com.socialcoisa.rede.themes.application.port.out.ThemeGalleryRepository;
import com.socialcoisa.rede.themes.domain.ThemePackage;

import tools.jackson.databind.ObjectMapper;

/** Synthetic balances only: assert ledger/snapshot equality as well as HTTP authorization. */
@SpringBootTest(properties = "rede.moderation.spam.enabled=false")
@AutoConfigureMockMvc
class EconomyIntegrityAuditTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired ThemeGalleryRepository themes;

    @Test
    void invalidAmountsCannotCreateMoneyOrChangeEitherWallet() throws Exception {
        var sender = auth("audit_amount_sender");
        var receiver = auth("audit_amount_receiver");
        long beforeSender = balance(sender), beforeReceiver = balance(receiver);
        for (long amount : List.of(0L, -1L, Long.MIN_VALUE, Long.MAX_VALUE)) {
            var result = transfer(sender, "audit-amount-receiver", amount, UUID.randomUUID().toString());
            assertThat(result.getResponse().getStatus()).isIn(400, 403);
        }
        assertThat(balance(sender)).isEqualTo(beforeSender);
        assertThat(balance(receiver)).isEqualTo(beforeReceiver);
        assertReconciled();
    }

    @Test
    void differentUsersMayReuseAKeyWithoutReplayingSomeoneElsesTransfer() throws Exception {
        var first = auth("audit_key_sender_a");
        var second = auth("audit_key_sender_b");
        var receiver = auth("audit_key_receiver");
        long a = balance(first), b = balance(second), c = balance(receiver);
        assertThat(transfer(first, "audit-key-receiver", 1000, "shared-audit-key")
            .getResponse().getStatus()).isEqualTo(200);
        assertThat(transfer(second, "audit-key-receiver", 1000, "shared-audit-key")
            .getResponse().getStatus()).isEqualTo(200);
        assertThat(balance(first)).isEqualTo(a - 1000);
        assertThat(balance(second)).isEqualTo(b - 1000);
        assertThat(balance(receiver)).isEqualTo(c + 2000);
        assertReconciled();
    }

    @Test
    void concurrentRetriesOfOneTransferMustOnlyDebitOnceAndNeverReturnServerErrors() throws Exception {
        var sender = auth("audit_race_transfer_sender");
        var receiver = auth("audit_race_transfer_receiver");
        long a = balance(sender), b = balance(receiver);
        List<Integer> statuses = parallel(12, i -> transfer(sender, "audit-race-transfer-receiver", 1000,
            "one-concurrent-transfer").getResponse().getStatus());
        assertThat(balance(sender)).isEqualTo(a - 1000);
        assertThat(balance(receiver)).isEqualTo(b + 1000);
        assertReconciled();
        // Replays may all complete with 200; a conflict is allowed, never required.
        assertThat(statuses).hasSize(12).contains(200).allMatch(status -> status == 200 || status == 409);
    }

    @Test
    void racingToBuyTheSameCosmeticMustNotChargeTwice() throws Exception {
        var buyer = auth("audit_race_cosmetic");
        long initial = balance(buyer);
        List<Integer> statuses = parallel(12, i -> command(buyer, "/loja/moldura-tempestade/comprar", null,
            "cosmetic-race-" + i).getResponse().getStatus());
        assertThat(balance(buyer)).isEqualTo(initial - 10_000);
        assertThat(db.queryForObject("select count(*) from coin_entries where account_id = ? and kind = 'PURCHASE'",
            Integer.class, buyer.userId())).isEqualTo(1);
        assertReconciled();
        assertThat(statuses).contains(200).containsOnly(200, 409);
    }

    @Test
    void anotherUserCannotConfirmOrRefundAPurchaseAndTerminalStatesCannotBeReplayed() throws Exception {
        var seller = auth("audit_market_seller");
        var buyer = auth("audit_market_buyer");
        var stranger = auth("audit_market_stranger");
        balance(seller);
        long initial = balance(buyer);
        // A controlled theme fixture isolates purchase authorization from theme schema validation.
        String themeId = "theme_" + UUID.randomUUID().toString().replace("-", "");
        themes.save(ThemePackage.create(themeId, seller.userId(), "Tema da auditoria", null, "{}", "light", Instant.now()));
        var listed = command(seller, "/marketplace", Map.of("itemId", themeId, "title", "Item sintetico", "price", 25_000),
            "audit-market-list").getResponse();
        assertThat(listed.getStatus()).as(listed.getContentAsString()).isEqualTo(200);
        String listingId = json.readTree(listed.getContentAsString()).get("id").asText();
        var bought = command(buyer, "/marketplace/" + listingId + "/buy", null, "audit-market-buy").getResponse();
        assertThat(bought.getStatus()).isEqualTo(200);
        String purchase = json.readTree(bought.getContentAsString()).get("purchaseId").asText();
        assertThat(balance(buyer)).isEqualTo(initial - 25_000);
        for (String action : List.of("confirm", "refund")) {
            assertThat(command(stranger, "/marketplace/purchases/" + purchase + "/" + action, null,
                "audit-market-foreign-" + action).getResponse().getStatus()).isEqualTo(403);
        }
        assertThat(command(buyer, "/marketplace/purchases/" + purchase + "/refund", null, "audit-market-refund")
            .getResponse().getStatus()).isEqualTo(200);
        assertThat(balance(buyer)).isEqualTo(initial);
        for (String action : List.of("confirm", "refund")) {
            assertThat(command(buyer, "/marketplace/purchases/" + purchase + "/" + action, null,
                "audit-market-terminal-" + action).getResponse().getStatus()).isEqualTo(409);
        }
        assertThat(balance(buyer)).isEqualTo(initial);
        assertReconciled();
    }

    private TestAuthHelper.AuthSession auth(String name) throws Exception {
        return new TestAuthHelper(mvc, json).authenticate(name);
    }
    private long balance(TestAuthHelper.AuthSession session) throws Exception {
        var response = mvc.perform(get("/carteira").header("X-Session-Token", session.token()))
            .andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
        return json.readTree(response.getContentAsString()).get("balance").asLong();
    }
    private MvcResult transfer(TestAuthHelper.AuthSession from, String recipient, long amount, String key) throws Exception {
        return command(from, "/carteira/transferencias", Map.of("toUsername", recipient, "amount", amount), key);
    }
    private MvcResult command(TestAuthHelper.AuthSession user, String path, Object body, String key) throws Exception {
        var request = post(path).header("X-Session-Token", user.token()).header("Idempotency-Key", key);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(request).andReturn();
    }
    @FunctionalInterface interface Attempt { int call(int index) throws Exception; }
    private List<Integer> parallel(int attempts, Attempt attempt) throws Exception {
        try (var pool = Executors.newFixedThreadPool(6)) {
            var jobs = new ArrayList<Callable<Integer>>();
            for (int i = 0; i < attempts; i++) { int index = i; jobs.add(() -> attempt.call(index)); }
            var results = new ArrayList<Integer>();
            for (var result : pool.invokeAll(jobs, 30, TimeUnit.SECONDS)) results.add(result.get());
            return results;
        }
    }
    private void assertReconciled() {
        assertThat(db.queryForObject("""
            select count(*) from coin_accounts a
            where a.balance <> (select coalesce(sum(e.amount), 0) from coin_entries e where e.account_id = a.account_id)
            """, Integer.class)).as("Saldos devem bater com os lancamentos depois de cada ataque").isZero();
    }
}
