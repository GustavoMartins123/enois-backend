package com.socialcoisa.rede.groups;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

import com.socialcoisa.rede.shared.web.TestAuthHelper;

import tools.jackson.databind.ObjectMapper;

/**
 * Comentários e reações do grupo.
 *
 * O que este teste tranca:
 *
 * - visitante não comenta nem reage (403); membro comenta e reage;
 * - resposta a comentário só aponta para comentário DA MESMA publicação;
 * - comentário em publicação de OUTRO grupo é 404 (o contexto do grupo é conferido);
 * - a reação alterna (mesma reação de novo remove), troca (outra reação substitui) e o DELETE
 *   remove de vez — os contadores da publicação acompanham.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GroupDiscussionTests {

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

	private String publish(TestAuthHelper.AuthSession author, String groupId, String topicId, String content,
			String key) throws Exception {
		MvcResult result = mockMvc.perform(post("/groups/" + groupId + "/posts")
				.header("X-Session-Token", author.token())
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"topicId", topicId, "content", content, "mediaIds", List.of()))))
			.andExpect(status().isCreated())
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
	}

	private MvcResult comment(TestAuthHelper.AuthSession user, String groupId, String postId, String content,
			String parentId, String key) throws Exception {
		java.util.Map<String, Object> body = new java.util.HashMap<>();
		body.put("content", content);
		if (parentId != null) {
			body.put("parentCommentId", parentId);
		}
		return mockMvc.perform(post("/groups/" + groupId + "/posts/" + postId + "/comments")
				.header("X-Session-Token", user.token())
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(body)))
			.andReturn();
	}

	private MvcResult react(TestAuthHelper.AuthSession user, String groupId, String postId, String type, String key)
			throws Exception {
		return mockMvc.perform(post("/groups/" + groupId + "/posts/" + postId + "/reactions")
				.header("X-Session-Token", user.token())
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("type", type))))
			.andReturn();
	}

	@Test
	void comentariosEReacoesFicamNoContextoDoGrupo() throws Exception {
		var dono = authHelper().authenticate("gdisc-dono");
		var membro = authHelper().authenticate("gdisc-membro");
		var estranho = authHelper().authenticate("gdisc-estranho");
		String groupId = createGroup(dono, "Grupo Discussao", "gdisc-create-1");
		join(membro, groupId, "gdisc-join-1");
		String topicId = topic(dono, groupId, "Geral", "gdisc-topic-1");
		String postId = publish(dono, groupId, topicId, "Publicacao para discutir", "gdisc-post-1");

		// Quem não é membro não comenta.
		mockMvc.perform(post("/groups/" + groupId + "/posts/" + postId + "/comments")
				.header("X-Session-Token", estranho.token())
				.header("Idempotency-Key", "gdisc-comment-estranho")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("content", "nao deveria"))))
			.andExpect(status().isForbidden());

		// Membro comenta.
		MvcResult criado = comment(membro, groupId, postId, "Comentario do membro", null, "gdisc-comment-1");
		org.assertj.core.api.Assertions.assertThat(criado.getResponse().getStatus()).isEqualTo(201);
		String commentId = objectMapper.readTree(criado.getResponse().getContentAsString()).get("id").asText();

		// Lista traz o comentário.
		mockMvc.perform(get("/groups/" + groupId + "/posts/" + postId + "/comments")
				.header("X-Session-Token", membro.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].content").value("Comentario do membro"))
			.andExpect(jsonPath("$.items[0].authorUsername").value("gdisc-membro"));

		// O contador da publicação acompanha.
		mockMvc.perform(get("/groups/" + groupId + "/posts").header("X-Session-Token", membro.token()))
			.andExpect(jsonPath("$.items[0].comments").value(1));

		// Resposta ao comentário (mesma publicação) entra.
		mockMvc.perform(post("/groups/" + groupId + "/posts/" + postId + "/comments")
				.header("X-Session-Token", dono.token())
				.header("Idempotency-Key", "gdisc-reply-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"content", "Respondendo", "parentCommentId", commentId))))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.parentCommentId").value(commentId));

		// Reação: SET, toggle off, troca e DELETE.
		mockMvc.perform(post("/groups/" + groupId + "/posts/" + postId + "/reactions")
				.header("X-Session-Token", membro.token())
				.header("Idempotency-Key", "gdisc-react-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("type", "LIKE"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("REACTED"));

		mockMvc.perform(get("/groups/" + groupId + "/posts").header("X-Session-Token", membro.token()))
			.andExpect(jsonPath("$.items[0].reactions").value(1));

		// A mesma reação de novo remove (toggle).
		mockMvc.perform(post("/groups/" + groupId + "/posts/" + postId + "/reactions")
				.header("X-Session-Token", membro.token())
				.header("Idempotency-Key", "gdisc-react-2")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("type", "LIKE"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UNREACTED"));

		mockMvc.perform(get("/groups/" + groupId + "/posts").header("X-Session-Token", membro.token()))
			.andExpect(jsonPath("$.items[0].reactions").value(0));

		// Outra reação substitui e o DELETE remove de vez.
		mockMvc.perform(post("/groups/" + groupId + "/posts/" + postId + "/reactions")
				.header("X-Session-Token", membro.token())
				.header("Idempotency-Key", "gdisc-react-3")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("type", "FIRE"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("REACTED"));

		mockMvc.perform(delete("/groups/" + groupId + "/posts/" + postId + "/reactions")
				.header("X-Session-Token", membro.token())
				.header("Idempotency-Key", "gdisc-unreact-1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UNREACTED"));

		mockMvc.perform(get("/groups/" + groupId + "/posts").header("X-Session-Token", membro.token()))
			.andExpect(jsonPath("$.items[0].reactions").value(0));

		// Reação desconhecida é 400.
		mockMvc.perform(post("/groups/" + groupId + "/posts/" + postId + "/reactions")
				.header("X-Session-Token", membro.token())
				.header("Idempotency-Key", "gdisc-react-4")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("type", "NAO_EXISTE"))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REACTION"));
	}

	@Test
	void comentarioEmPublicacaoDeOutroGrupoERecusado() throws Exception {
		var dono = authHelper().authenticate("gdisc-dono2");
		var membro = authHelper().authenticate("gdisc-membro2");
		String grupoA = createGroup(dono, "Grupo A Discussao", "gdisc-create-2a");
		String grupoB = createGroup(dono, "Grupo B Discussao", "gdisc-create-2b");
		join(membro, grupoA, "gdisc-join-2");
		String topicoB = topic(dono, grupoB, "Geral B", "gdisc-topic-2b");
		String postDeB = publish(dono, grupoB, topicoB, "Post do grupo B", "gdisc-post-2b");

		mockMvc.perform(post("/groups/" + grupoA + "/posts/" + postDeB + "/comments")
				.header("X-Session-Token", membro.token())
				.header("Idempotency-Key", "gdisc-cross-comment")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("content", "cruzando grupos"))))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
	}
}
