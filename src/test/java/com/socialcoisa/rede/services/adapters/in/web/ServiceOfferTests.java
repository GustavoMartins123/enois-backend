package com.socialcoisa.rede.services.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.ObjectMapper;
import com.socialcoisa.rede.shared.web.TestAuthHelper;

/**
 * O diretório de serviços — a mesma mecânica dos jogos: envia, a moderação publica ou recusa,
 * o dono edita. Os testes cobrem o caminho completo: moderação, catálogo público, dono e link.
 *
 * Os links usam itch.io de propósito: o guard resolve DNS de verdade, então o domínio precisa
 * existir (o mesmo padrão dos testes de jogos).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ServiceOfferTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	private TestAuthHelper auth;

	@BeforeEach
	void setUp() {
		auth = new TestAuthHelper(mockMvc, objectMapper);
	}

	@Test
	void submitThenApproveThenCatalogThenGuestReads() throws Exception {
		var author = auth.authenticate("svc_author1");
		String serviceId = submit(author, "Site institucional", "Crio sites institucionais completos...",
			"https://creator.itch.io/servico-dev", "DESENVOLVIMENTO");
		String slug = slugOf(author, serviceId);

		// Antes da moderação: some do catálogo e da página pública.
		mockMvc.perform(get("/services").param("q", "Site institucional"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.id == '" + serviceId + "')]").isEmpty());

		// O dono abre a própria em /mine (qualquer status).
		mockMvc.perform(get("/services/mine").header("X-Session-Token", author.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.id == '" + serviceId + "')].status").value("SUBMITTED"));

		// O admin aprova pelo painel; a oferta entra no catálogo.
		var admin = auth.authenticate("group-admin");
		mockMvc.perform(post("/moderation/services/" + serviceId + "/approve")
				.header("X-Session-Token", admin.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(java.util.Map.of("note", "bom material"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PUBLISHED"));

		mockMvc.perform(get("/services").param("category", "DESENVOLVIMENTO").param("q", "Site institucional"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.id == '" + serviceId + "')].username").value("svc-author1"));

		// Página pública: o visitante lê e NÃO é dono.
		mockMvc.perform(get("/services/svc-author1/" + slug))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.owned").value(false));

		// Usuário comum não acessa a fila do painel.
		var outsider = auth.authenticate("svc_outsider");
		mockMvc.perform(get("/moderation/services").header("X-Session-Token", outsider.token()))
			.andExpect(status().isForbidden());
	}

	@Test
	void submitWithoutSessionIsRejected() throws Exception {
		// Sem o header de sessão, o pedido nem chega a ser de serviço: 401 (sessão ausente).
		mockMvc.perform(post("/services")
				.header("X-Session-Token", "")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(java.util.Map.of(
					"title", "Consultoria", "description", "Ajudo com consultorias...", "link", "",
					"category", "OUTROS"))))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void linkValidationBlocksInsecureAndPrivateTargets() throws Exception {
		var author = auth.authenticate("svc_author2");

		// javascript:// e http nunca.
		mockMvc.perform(post("/services")
				.header("X-Session-Token", author.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(java.util.Map.of(
					"title", "Design de produto", "description", "Faço design de produto digital...",
					"link", "javascript:alert(1)", "category", "DESIGN"))))
			.andExpect(status().isBadRequest());

		mockMvc.perform(post("/services")
				.header("X-Session-Token", author.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(java.util.Map.of(
					"title", "Design de produto", "description", "Faço design de produto digital...",
					"link", "http://localhost:8080", "category", "DESIGN"))))
			.andExpect(status().isBadRequest());

		// https público entra; sem link também entra (o catálogo não exige link).
		String comLink = submit(author, "Design de produto", "Faço design de produto digital...",
			"https://creator.itch.io/servico-portfolio", "DESIGN");
		String semLink = submit(author, "Design sem portfolio", "Faço design sem portfolio público...", "",
			"DESIGN");

		var mine = mockMvc.perform(get("/services/mine").header("X-Session-Token", author.token()))
			.andExpect(status().isOk())
			.andReturn();
		var items = objectMapper.readTree(mine.getResponse().getContentAsString());
		for (var item : items) {
			if (comLink.equals(item.get("id").asText())) {
				assertThat(item.get("link").asText()).isEqualTo("https://creator.itch.io/servico-portfolio");
			}
			if (semLink.equals(item.get("id").asText())) {
				assertThat(item.has("link") && !item.get("link").isNull()).isFalse();
			}
		}
	}

	@Test
	void ownerEditsAndOutsiderCannot() throws Exception {
		var author = auth.authenticate("svc_author3");
		String serviceId = submit(author, "Copywriting de vendas", "Escrevo textos de vendas que convertem...",
			"", "COPYWRITING");

		var outsider = auth.authenticate("svc_outsider2");
		mockMvc.perform(patch("/services/" + serviceId)
				.header("X-Session-Token", outsider.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(java.util.Map.of(
					"title", "Texto alheio", "description", "Tentativa de editar texto alheio...",
					"link", "", "category", "COPYWRITING"))))
			.andExpect(status().isForbidden());

		// O admin publica; o dono edita o título e o serviço mantém o mesmo slug.
		var admin = auth.authenticate("group-admin");
		mockMvc.perform(post("/moderation/services/" + serviceId + "/approve")
				.header("X-Session-Token", admin.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(java.util.Map.of())))
			.andExpect(status().isOk());

		mockMvc.perform(patch("/services/" + serviceId)
				.header("X-Session-Token", author.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(java.util.Map.of(
					"title", "Copywriting que converte", "description",
					"Escrevo textos de vendas que convertem...", "link", "", "category", "COPYWRITING"))))
			.andExpect(status().isOk());

		mockMvc.perform(get("/services/mine").header("X-Session-Token", author.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.id == '" + serviceId + "')].title").value("Copywriting que converte"));

		var outsider2 = auth.authenticate("svc_outsider3");
		mockMvc.perform(delete("/services/" + serviceId).header("X-Session-Token", outsider2.token()))
			.andExpect(status().isForbidden());
		mockMvc.perform(delete("/services/" + serviceId).header("X-Session-Token", author.token()))
			.andExpect(status().isOk());
	}

	@Test
	void rejectRequiresReasonAndResubmitReturnsToModeration() throws Exception {
		var author = auth.authenticate("svc_author4");
		String serviceId = submit(author, "SEO para pequenas", "Faço SEO para pequenas empresas...",
			"", "SEO");

		var admin = auth.authenticate("group-admin");

		// Sem motivo, recusa não existe: o prestador precisa do porquê.
		mockMvc.perform(post("/moderation/services/" + serviceId + "/reject")
				.header("X-Session-Token", admin.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(java.util.Map.of())))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("REASON_REQUIRED"));

		// Com motivo, a recusa devolve o reviewNote (o que o prestador vai ler).
		mockMvc.perform(post("/moderation/services/" + serviceId + "/reject")
				.header("X-Session-Token", admin.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(java.util.Map.of("note", "descreva o público alvo"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("REJECTED"))
			.andExpect(jsonPath("$.reviewNote").value("descreva o público alvo"));

		// O dono reenvia: volta para a fila de moderação.
		mockMvc.perform(post("/services/" + serviceId + "/resubmit")
				.header("X-Session-Token", author.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SUBMITTED"));
	}

	@Test
	void suspendedLeavesTheCatalog() throws Exception {
		var author = auth.authenticate("svc_author5");
		String serviceId = submit(author, "Marketing de conteudo", "Crio marketing de conteúdo...",
			"", "MARKETING");

		var admin = auth.authenticate("group-admin");
		mockMvc.perform(post("/moderation/services/" + serviceId + "/approve")
				.header("X-Session-Token", admin.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(java.util.Map.of())))
			.andExpect(status().isOk());

		mockMvc.perform(get("/services").param("q", "Marketing de conteudo"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.id == '" + serviceId + "')]").isNotEmpty());

		// A moderação tira do ar: some do catálogo, o registro continua no /mine.
		mockMvc.perform(post("/moderation/services/" + serviceId + "/suspend")
				.header("X-Session-Token", admin.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(java.util.Map.of("note", "descreva preço"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SUSPENDED"));

		mockMvc.perform(get("/services").param("q", "Marketing de conteudo"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.id == '" + serviceId + "')]").isEmpty());
		mockMvc.perform(get("/services/mine").header("X-Session-Token", author.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.id == '" + serviceId + "')].status").value("SUSPENDED"));
	}

	private String submit(TestAuthHelper.AuthSession author, String title, String description, String link,
			String category) throws Exception {
		MvcResult result = mockMvc.perform(post("/services")
				.header("X-Session-Token", author.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(java.util.Map.of(
					"title", title, "description", description, "link", link,
					"category", category))))
			.andExpect(status().isOk())
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
	}

	private String slugOf(TestAuthHelper.AuthSession author, String serviceId) throws Exception {
		var mine = mockMvc.perform(get("/services/mine").header("X-Session-Token", author.token()))
			.andExpect(status().isOk())
			.andReturn();
		var items = objectMapper.readTree(mine.getResponse().getContentAsString());
		for (var item : items) {
			if (serviceId.equals(item.get("id").asText())) {
				return item.get("slug").asText();
			}
		}
		throw new IllegalStateException("serviço não encontrado no /mine: " + serviceId);
	}
}
