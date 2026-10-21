package com.socialcoisa.rede.securityaudit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.socialcoisa.rede.shared.web.TestAuthHelper;

import tools.jackson.databind.ObjectMapper;

/** The payload travels through publication, storage, editing and the delivered HTML document. */
@SpringBootTest(properties = "rede.moderation.spam.enabled=false")
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StoredHtmlSecurityAuditTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired org.springframework.jdbc.core.JdbcTemplate db;
    private TestAuthHelper.AuthSession author;

    @BeforeAll void account() throws Exception {
        author = new TestAuthHelper(mvc, json).authenticate("audit_xss_pages");
    }

    static Stream<Arguments> attacks() {
        return Stream.of(
            Arguments.of("script", "<script>window.auditExecuted=1</script>"),
            Arguments.of("event", "<img src=x onerror='window.auditExecuted=1'>"),
            Arguments.of("svg-event", "<svg onload='window.auditExecuted=1'><circle r=3/></svg>"),
            Arguments.of("mixed-case", "<a href='JaVaScRiPt:window.auditExecuted=1'>link</a>"),
            Arguments.of("entity-scheme", "<a href='&#106;avascript:window.auditExecuted=1'>link</a>"),
            Arguments.of("tab-scheme", "<a href='java&#9;script:window.auditExecuted=1'>link</a>"),
            Arguments.of("srcdoc", "<iframe srcdoc=\"<script>window.auditExecuted=1</script>\"></iframe>"),
            Arguments.of("provider-impersonation", "<iframe src='https://www.youtube.com.audit.invalid/embed/a'></iframe>"),
            Arguments.of("provider-userinfo", "<iframe src='https://www.youtube.com@audit.invalid/embed/a'></iframe>"),
            Arguments.of("foreign-object", "<svg><foreignObject><img src=x onerror='window.auditExecuted=1'></foreignObject></svg>"),
            Arguments.of("math-mutation", "<math><mtext><table><mglyph><style><!--</style><img title='--><img src=x onerror=window.auditExecuted=1>'>"),
            Arguments.of("css-expression", "<p style='width:expression(window.auditExecuted=1)'>css</p>"),
            Arguments.of("css-import", "<style>@import 'https://audit.invalid/remote.css';</style>"),
            Arguments.of("meta-refresh", "<meta http-equiv=refresh content='0;url=https://audit.invalid'>"),
            Arguments.of("base-url", "<base href='https://audit.invalid/'>"),
            Arguments.of("svg-set-url", "<svg><a><set attributeName='href' to='javascript:window.auditExecuted=1'/><text y='20'>link</text></a></svg>"),
            Arguments.of("svg-animate-url", "<svg><a href='https://example.invalid/'><animate attributeName='href' to='javascript:window.auditExecuted=1' fill='freeze'/><text y='20'>link</text></a></svg>"),
            Arguments.of("css-escaped-function", "<style>p{background:u\\72l(https://audit.invalid/pixel)}</style>"),
            Arguments.of("css-image-set-string", "<style>p{background:image-set('https://audit.invalid/pixel' 1x)}</style>"),
            Arguments.of("css-comment-escape", "<style>p{background:u/**/\\72l(https://audit.invalid/pixel)}</style>"),
            Arguments.of("css-inline-escape", "<p style='background:u\\72l(https://audit.invalid/pixel)'>css</p>"),
            Arguments.of("svg-animate-values", "<svg><a><animate attributeName='href' values='https://example.invalid/;javascript:window.auditExecuted=1'/></a></svg>")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("attacks")
    void neitherCreationNorEditingCanStoreActivePayloads(String name, String payload) throws Exception {
        String safeSuffix = "<p>conteudo legitimo preservado " + name + "</p>";
        var created = mvc.perform(post("/feed/posts").header("X-Session-Token", author.token())
            .header("Idempotency-Key", "xss-create-" + name).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("type", "PAGE", "content", "Auditoria " + name,
                "pageHtml", payload + safeSuffix)))).andReturn().getResponse();
        assertThat(created.getStatus()).as(created.getContentAsString()).isEqualTo(201);
        String postId = json.readTree(created.getContentAsString()).get("id").asText();
        assertInert(persistedHtml(postId), name + " create");
        assertInert(deliveredHtml(postId), name + " JSON create");
        var edited = mvc.perform(patch("/feed/posts/" + postId).header("X-Session-Token", author.token())
            .header("Idempotency-Key", "xss-edit-" + name).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("content", "Editado " + name,
                "pageHtml", payload + safeSuffix)))).andReturn().getResponse();
        assertThat(edited.getStatus()).as(edited.getContentAsString()).isEqualTo(200);
        assertInert(persistedHtml(postId), name + " edit");
        assertInert(deliveredHtml(postId), name + " JSON edit");

        var document = mvc.perform(get("/pages/" + postId)).andReturn().getResponse();
        assertThat(document.getStatus()).isEqualTo(200);
        assertThat(document.getContentType()).startsWith("text/html");
        assertThat(document.getHeader("Content-Security-Policy"))
            .contains("script-src 'none'", "connect-src 'none'", "form-action 'none'", "base-uri 'none'");
        assertThat(document.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(document.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
    }

    @org.junit.jupiter.api.Test
    void previouslyStoredPayloadsAreCleanedOnBothDeliveryPaths() throws Exception {
        var created = mvc.perform(post("/feed/posts").header("X-Session-Token", author.token())
            .header("Idempotency-Key", "legacy-html-create").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("type", "PAGE", "pageHtml", "<p>legacy fixture</p>"))))
            .andReturn().getResponse();
        assertThat(created.getStatus()).isEqualTo(201);
        String id = json.readTree(created.getContentAsString()).get("id").asText();
        String legacy = "<style>p{background:u\\72l(https://audit.invalid/pixel)}</style>"
            + "<svg><a><set attributeName='href' to='javascript:window.auditExecuted=1'/></a></svg>"
            + "<p>conteudo legitimo preservado</p>";
        db.update("update posts set page_html = ? where id = ?", legacy, id);
        assertInert(deliveredHtml(id), "legacy JSON");
        var document = mvc.perform(get("/pages/" + id)).andReturn().getResponse();
        assertThat(document.getStatus()).isEqualTo(200);
        assertInert(document.getContentAsString(), "legacy document");
    }

    private String persistedHtml(String postId) {
        return db.queryForObject("select page_html from posts where id = ?", String.class, postId);
    }

    private String deliveredHtml(String postId) throws Exception {
        var response = mvc.perform(get("/feed/posts/" + postId).header("X-Session-Token", author.token()))
            .andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        return json.readTree(response.getContentAsString()).get("pageHtml").asText();
    }

    private void assertInert(String html, String stage) throws Exception {
        var directory = java.nio.file.Path.of("target/security-audit/html");
        java.nio.file.Files.createDirectories(directory);
        java.nio.file.Files.writeString(directory.resolve(stage.replace(' ', '-') + ".html"), html);
        var document = Jsoup.parseBodyFragment(html);
        assertThat(document.text()).as(stage).contains("conteudo legitimo preservado");
        assertThat(document.select("script, iframe, object, embed, foreignObject, form, input, meta, base, link"))
            .as(stage).isEmpty();
        for (Element element : document.getAllElements()) {
            element.attributes().forEach(attribute -> {
                String key = attribute.getKey().toLowerCase(Locale.ROOT);
                String value = attribute.getValue().replaceAll("[\\x00-\\x20]", "").toLowerCase(Locale.ROOT);
                assertThat(key.startsWith("on")).as(stage + " event attribute " + key).isFalse();
                if (key.equals("href") || key.equals("src") || key.equals("xlink:href")
                        || key.equals("to") || key.equals("from")) {
                    assertThat(value).as(stage + " executable URL via " + key)
                        .doesNotStartWith("javascript:").doesNotStartWith("vbscript:")
                        .doesNotStartWith("data:text/html");
                }
            });
        }
        assertThat(html).as(stage + " invisible remote CSS load").doesNotContain("audit.invalid");
    }
}
