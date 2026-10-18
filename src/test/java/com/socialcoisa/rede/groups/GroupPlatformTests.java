package com.socialcoisa.rede.groups;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;

import com.socialcoisa.rede.shared.web.TestAuthHelper;

import tools.jackson.databind.ObjectMapper;

/**
 * Estados do grupo pela PLATAFORMA (restrito, suspenso, reativado).
 *
 * O que este teste tranca:
 *
 * - só administrador da plataforma muda o estado (a allowlist de teste é `group-admin@test.com`);
 * - RESTRICTED para de entrar gente e o conteúdo segue;
 * - SUSPENDED congela entrada e conteúdo, some da descoberta e o que existe continua legível para
 *   quem já é membro (congelado, não apagado);
 * - reativar devolve tudo;
 * - estado desconhecido e "CLOSED" pela plataforma são recusados (encerrar é do dono).
 */
@SpringBootTest
@AutoConfigureMockMvc
class GroupPlatformTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	private TestAuthHelper authHelper() {
		return new TestAuthHelper(mockMvc, objectMapper);
	}

	private String createGroup(TestAuthHelper.AuthSession owner, String name, String key) throws Exception {
		MvcResult result = mockMvc.perform(post("/groups")
				.header("X-Session-Token", owner.token())
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("name", name, "joinMode", "OPEN"))))
			.andExpect(status().isCreated())
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
	}

	private void join(TestAuthHelper.AuthSession user, String groupId, String key) throws Exception {
		mockMvc.perform(post("/groups/" + groupId + "/join")
				.header("X-Session-Token", user.token())
				.header("Idempotency-Key", key))
			.andExpect(status().isOk());
	}

	private String topic(TestAuthHelper.AuthSession admin, String groupId, String name, String key) throws Exception {
		MvcResult result = mockMvc.perform(post("/groups/" + groupId + "/topics")
				.header("X-Session-Token", admin.token())
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("name", name))))
			.andExpect(status().isCreated())
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
	}

	private RequestBuilder publish(TestAuthHelper.AuthSession author, String groupId, String topicId,
			String content, String key) throws Exception {
		return post("/groups/" + groupId + "/posts")
			.header("X-Session-Token", author.token())
			.header("Idempotency-Key", key)
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(Map.of(
				"topicId", topicId, "content", content, "mediaIds", List.of())));
	}

	/** Nome diferente de `status` de propósito: `status()` é o matcher do MockMvc. */
	private RequestBuilder changeStatus(TestAuthHelper.AuthSession actor, String groupId, String value,
			String key) throws Exception {
		return post("/groups/" + groupId + "/status")
			.header("X-Session-Token", actor.token())
			.header("Idempotency-Key", key)
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(Map.of("status", value, "reason", "revisao da plataforma")));
	}

	@Test
	void plataformaRestringeSuspendeEReativa() throws Exception {
		var dono = authHelper().authenticate("gplat-dono");
		var admin = authHelper().authenticate("group-admin");
		var membro = authHelper().authenticate("gplat-membro");
		String groupId = createGroup(dono, "Grupo Plataforma", "gplat-create-1");
		join(membro, groupId, "gplat-join-1");
		String topicId = topic(dono, groupId, "Geral", "gplat-topic-1");

		// Quem não é admin da plataforma não muda estado.
		mockMvc.perform(post("/groups/" + groupId + "/status")
				.header("X-Session-Token", membro.token())
				.header("Idempotency-Key", "gplat-status-membro")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("status", "SUSPENDED"))))
			.andExpect(status().isForbidden());

		// RESTRICTED: para de entrar gente, conteúdo segue.
		mockMvc.perform(post("/groups/" + groupId + "/status")
				.header("X-Session-Token", admin.token())
				.header("Idempotency-Key", "gplat-status-restrito")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("status", "RESTRICTED", "reason", "revisao"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("RESTRICTED"));

		var novato = authHelper().authenticate("gplat-novato");
		mockMvc.perform(post("/groups/" + groupId + "/join")
				.header("X-Session-Token", novato.token())
				.header("Idempotency-Key", "gplat-join-novato"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("GROUP_NOT_ACCEPTING_MEMBERS"));

		mockMvc.perform(publish(dono, groupId, topicId, "publica no restrito", "gplat-post-restrito"))
			.andExpect(status().isCreated());

		// SUSPENDED: conteúdo congela, some da descoberta, leitura continua para membro.
		mockMvc.perform(post("/groups/" + groupId + "/status")
				.header("X-Session-Token", admin.token())
				.header("Idempotency-Key", "gplat-status-suspenso")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("status", "SUSPENDED", "reason", "investigacao"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("SUSPENDED"));

		mockMvc.perform(publish(dono, groupId, topicId, "nao pode publicar", "gplat-post-suspenso"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("GROUP_NOT_ACCEPTING_CONTENT"));

		mockMvc.perform(get("/groups").param("query", "Plataforma"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(0));

		mockMvc.perform(get("/groups/" + groupId + "/posts").header("X-Session-Token", membro.token()))
			.andExpect(status().isOk());

		// Reativado: tudo volta.
		mockMvc.perform(post("/groups/" + groupId + "/status")
				.header("X-Session-Token", admin.token())
				.header("Idempotency-Key", "gplat-status-ativo")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("status", "ACTIVE", "reason", "liberado"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ACTIVE"));

		mockMvc.perform(publish(dono, groupId, topicId, "publica de novo", "gplat-post-ativo"))
			.andExpect(status().isCreated());

		mockMvc.perform(get("/groups").param("query", "Plataforma"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(1));
	}

	@Test
	void estadoDesconhecidoEEncerrarPelaPlataformaSaoRecusados() throws Exception {
		var dono = authHelper().authenticate("gplat-dono2");
		var admin = authHelper().authenticate("group-admin");
		String groupId = createGroup(dono, "Grupo Estados", "gplat-create-2");

		mockMvc.perform(changeStatus(admin, groupId, "NAO_EXISTE", "gplat-status-invalido"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_STATUS"));

		mockMvc.perform(changeStatus(admin, groupId, "CLOSED", "gplat-status-closed"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_STATUS"));
	}
}
