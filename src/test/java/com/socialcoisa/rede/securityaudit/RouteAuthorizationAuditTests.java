package com.socialcoisa.rede.securityaudit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.socialcoisa.rede.shared.web.TestAuthHelper;
import com.socialcoisa.rede.shared.web.TestDatabase;

import tools.jackson.databind.ObjectMapper;

/** Checks the real MVC route registry; new write/admin routes join the audit automatically. */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RouteAuthorizationAuditTests {

    // These are the public authentication/bootstrap contracts, not a wildcard exception.
    private static final Set<String> PUBLIC_WRITES = Set.of(
        "/auth/signup", "/auth/login", "/auth/refresh", "/auth/email/verify", "/auth/email/resend",
        "/auth/password/forgot", "/auth/password/reset", "/auth/2fa/verify", "/auth/2fa/resend",
        "/auth/2fa/email/request", "/auth/2fa/email/confirm", "/games/tokens/validate",
        "/auth/oauth/{provider}/callback", "/auth/oauth/{provider}/token");
    private static final Set<String> WRITES = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final Set<String> PRIVATE_READS = Set.of(
        "/age/parental/mine", "/auth/2fa/status", "/auth/me", "/auth/oauth/providers", "/blogs/me",
        "/communities/favorites", "/communities/mine", "/daily-games/blocks/today", "/daily-games/maze/today",
        "/daily-games/words/today", "/empresas/mine", "/empresas/{companyId}/members",
        "/feed/saved", "/feed/scheduled", "/games/library", "/games/profile/mine", "/games/{gameId}/analytics",
        "/identity", "/identity/eligibility", "/jobs/applications", "/jobs/applications/mine",
        "/jobs/external/mine", "/jobs/mine", "/jobs/saved", "/jobs/{jobId}/applications",
        "/marketplace/compras", "/marketplace/mine", "/marketplace/vendas", "/media/migration",
        "/notifications", "/reports/mine", "/services/mine", "/servicos/minhas",
        "/servicos/pedidos/{requestId}/propostas", "/temas/galeria/mine");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;
    private TestAuthHelper.AuthSession ordinary;
    private String expiredToken;
    private List<Route> routes;

    record Route(String method, String pattern, MediaType consumes, String handler) {
        String path() {
            return pattern.replace("{ip}", "192.0.2.250")
                .replaceAll("\\{[^}]+}", "audit_missing_resource");
        }
        boolean admin() { return pattern.equals("/admin") || pattern.startsWith("/admin/")
            || pattern.equals("/media/migration"); }
        boolean write() { return WRITES.contains(method); }
        boolean privateRead() {
            return method.equals("GET") && (PRIVATE_READS.contains(pattern) || pattern.equals("/me")
                || pattern.startsWith("/me/") || pattern.equals("/carteira") || pattern.startsWith("/carteira/")
                || pattern.equals("/conversations") || pattern.startsWith("/conversations/")
                || pattern.startsWith("/network/") || pattern.startsWith("/moderation/")
                || pattern.matches("/groups/\\{[^}]+}/.+"));
        }
        String label() { return method + " " + pattern; }
    }

    @BeforeAll
    void inventory() throws Exception {
        var auth = new TestAuthHelper(mvc, json);
        ordinary = auth.authenticate("audit_route_ordinary");
        var expired = auth.authenticate("audit_route_expired");
        expiredToken = expired.token();
        TestDatabase.expireAccessToken(expiredToken, Instant.now().minusSeconds(60));
        routes = new ArrayList<>();
        mappings.getHandlerMethods().forEach((mapping, handler) -> {
            if (!handler.getBeanType().getPackageName().startsWith("com.socialcoisa.rede")) return;
            MediaType consumes = mapping.getConsumesCondition().getConsumableMediaTypes().stream()
                .filter(type -> !type.isWildcardType()).findFirst().orElse(MediaType.APPLICATION_JSON);
            mapping.getPatternValues().forEach(pattern ->
                mapping.getMethodsCondition().getMethods().forEach(method ->
                    routes.add(new Route(method.name(), pattern, consumes,
                        handler.getBeanType().getSimpleName() + "#" + handler.getMethod().getName()))));
        });
        routes.sort(Comparator.comparing(Route::pattern).thenComparing(Route::method));
        Path output = Path.of("target/security-audit/routes.tsv");
        Files.createDirectories(output.getParent());
        var lines = new ArrayList<String>();
        lines.add("method\tpath\thandler\tcentral_authorization_probe");
        routes.forEach(route -> lines.add(route.method() + "\t" + route.pattern() + "\t" + route.handler()
            + "\t" + (route.admin() ? "anonymous/forged/invalid/expired/non-admin"
                : (route.write() && !PUBLIC_WRITES.contains(route.pattern())) || route.privateRead()
                    ? "anonymous/forged/invalid/expired"
                    : route.method().equals("GET") ? "public-read-input-fuzz; domain-specific tests required"
                        : "public-bootstrap; domain-specific tests required")));
        Files.write(output, lines, StandardCharsets.UTF_8);
    }

    @Test
    void inventoryCannotSilentlyLoseControllers() {
        assertThat(routes.size()).isGreaterThan(350);
        assertThat(routes.stream().filter(Route::admin).count()).isGreaterThan(25);
        assertThat(routes.stream().filter(Route::write).count()).isGreaterThan(200);
    }

    @TestFactory
    Stream<DynamicTest> everyProtectedWriteRejectsFourUnauthenticatedIdentities() {
        return routes.stream().filter(route -> route.write() && !PUBLIC_WRITES.contains(route.pattern()))
            .flatMap(route -> Stream.of("anonymous", "forged-user-id", "invalid-token", "expired-token")
                .map(identity -> dynamicTest(route.label() + " [" + identity + "]", () -> {
                    var builder = build(route);
                    if (identity.equals("forged-user-id")) builder.header("X-User-Id", ordinary.userId());
                    if (identity.equals("invalid-token")) builder.header("X-Session-Token", "audit_invalid");
                    if (identity.equals("expired-token")) builder.header("X-Session-Token", expiredToken);
                    var response = mvc.perform(builder).andReturn().getResponse();
                    assertThat(response.getStatus()).as(route.label() + " [" + identity + "] "
                        + response.getContentAsString()).isEqualTo(401);
                })));
    }

    @TestFactory
    Stream<DynamicTest> privateAndOperationalReadsRejectFourUnauthenticatedIdentities() {
        return routes.stream().filter(Route::privateRead)
            .flatMap(route -> Stream.of("anonymous", "forged-user-id", "invalid-token", "expired-token")
                .map(identity -> dynamicTest(route.label() + " [" + identity + "]", () -> {
                    var builder = build(route);
                    if (identity.equals("forged-user-id")) builder.header("X-User-Id", ordinary.userId());
                    if (identity.equals("invalid-token")) builder.header("X-Session-Token", "audit_invalid");
                    if (identity.equals("expired-token")) builder.header("X-Session-Token", expiredToken);
                    var response = mvc.perform(builder).andReturn().getResponse();
                    assertThat(response.getStatus()).as(route.label() + " [" + identity + "] "
                        + response.getContentAsString()).isEqualTo(401);
                })));
    }

    @TestFactory
    Stream<DynamicTest> everyAdminRouteRejectsOrdinaryUsersIncludingForgedHeaders() {
        return routes.stream().filter(Route::admin).map(route -> dynamicTest(route.label(), () -> {
            var response = mvc.perform(build(route)
                .header("X-Session-Token", ordinary.token())
                .header("X-User-Id", "group-admin")
                .header("X-Role", "ADMIN").header("X-Admin", "true"))
                .andReturn().getResponse();
            assertThat(response.getStatus()).as(route.label() + " " + response.getContentAsString())
                .isEqualTo(403);
        }));
    }

    @TestFactory
    Stream<DynamicTest> everyAdminReadRejectsAnonymousAccess() {
        return routes.stream().filter(route -> route.admin() && !route.write())
            .map(route -> dynamicTest(route.label(), () ->
                assertThat(mvc.perform(build(route)).andReturn().getResponse().getStatus()).isEqualTo(401)));
    }

    @TestFactory
    Stream<DynamicTest> publicReadInputErrorsMustNotBecomeInternalServerErrors() {
        return routes.stream().filter(route -> route.method().equals("GET") && !route.admin() && !route.privateRead())
            .flatMap(route -> Stream.of("zero-limit", "negative-limit", "malformed-cursor")
                .map(probe -> dynamicTest(route.label() + " [" + probe + "]", () -> {
                    var builder = build(route);
                    if (probe.equals("zero-limit")) builder.param("limit", "0");
                    if (probe.equals("negative-limit")) builder.param("limit", "-1");
                    if (probe.equals("malformed-cursor")) builder.param("cursor", "not-a-valid-cursor-%");
                    var response = mvc.perform(builder).andReturn().getResponse();
                    assertThat(response.getStatus()).as(route.label() + " [" + probe + "] "
                        + response.getContentAsString()).isNotEqualTo(500);
                })));
    }

    private AbstractMockHttpServletRequestBuilder<?> build(Route route) {
        var method = HttpMethod.valueOf(route.method());
        AbstractMockHttpServletRequestBuilder<?> builder = MediaType.MULTIPART_FORM_DATA.isCompatibleWith(route.consumes())
            ? multipart(method, route.path()) : request(method, route.path()).contentType(route.consumes()).content("{}");
        return builder.header("Idempotency-Key", "audit-denied-" + route.label().hashCode());
    }
}
