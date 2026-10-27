package com.socialcoisa.rede.feed.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.socialcoisa.rede.profile.application.port.out.NotePenaltyReader;
import com.socialcoisa.rede.shared.web.TestAuthHelper;

import tools.jackson.databind.ObjectMapper;

/**
 * Nota de rodapé: enviada por outra pessoa, nasce em análise, só aparece aprovada e a negada pesa
 * na credibilidade. Enviar nota aprovada (ou pendente) não muda a credibilidade.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PostNoteFlowTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private NotePenaltyReader notePenalties;

	private TestAuthHelper auth;

	@BeforeEach
	void setUp() {
		auth = new TestAuthHelper(mockMvc, objectMapper);
	}

	private String publicar(String token, String key) throws Exception {
		MvcResult result = mockMvc.perform(post("/feed/posts")
				.header("X-Session-Token", token)
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"type", "INSIGHT", "content", "Post que será corrigido pela comunidade.", "tags", List.of(),
					"mediaIds", List.of()))))
			.andExpect(status().isCreated())
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asString();
	}

	private String enviarNota(String token, String postId, String body, String key) throws Exception {
		MvcResult result = mockMvc.perform(post("/feed/posts/" + postId + "/notes")
				.header("X-Session-Token", token)
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("body", body))))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("PENDING"))
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asString();
	}

	@Test
	void aprovadaApareceNoPostENegadaPesaNaCredibilidade() throws Exception {
		var autor = auth.authenticate("note_autor_" + System.nanoTime());
		var comunidade = auth.authenticate("note_comunidade_" + System.nanoTime());
		var equipe = auth.authenticate("group-admin");
		String postId = publicar(autor.token(), "note-pub-1");

		// Antes da aprovação a nota não é pública e a credibilidade não muda.
		String aprovada = enviarNota(comunidade.token(), postId,
			"A informação correta é 42%, conforme o relatório oficial.", "note-create-1");
		mockMvc.perform(get("/feed/posts/" + postId + "/notes"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(0));
		assertThat(notePenalties.countDeniedByUser(comunidade.userId())).isZero();

		mockMvc.perform(patch("/admin/notes/" + aprovada)
				.header("X-Session-Token", equipe.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("status", "APPROVED"))))
			.andExpect(status().isOk());

		mockMvc.perform(get("/feed/posts/" + postId + "/notes"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].body").value("A informação correta é 42%, conforme o relatório oficial."));

		mockMvc.perform(get("/feed/posts/" + postId).header("X-Session-Token", autor.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.communityNotes.length()").value(1));
		assertThat(notePenalties.countDeniedByUser(comunidade.userId())).isZero();

		// Outra pessoa manda uma nota falsa: negada não aparece e conta para a credibilidade.
		var outra = auth.authenticate("note_falsa_" + System.nanoTime());
		String negada = enviarNota(outra.token(), postId,
			"Na verdade o número correto é 90% e ninguém prova o contrário.", "note-create-2");

		mockMvc.perform(patch("/admin/notes/" + negada)
				.header("X-Session-Token", equipe.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("status", "DENIED"))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("NOTE_REASON_REQUIRED"));

		mockMvc.perform(patch("/admin/notes/" + negada)
				.header("X-Session-Token", equipe.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"status", "DENIED", "reason", "Número inventado, sem fonte."))))
			.andExpect(status().isOk());

		mockMvc.perform(get("/feed/posts/" + postId + "/notes"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(1));
		assertThat(notePenalties.countDeniedByUser(outra.userId())).isEqualTo(1);
	}

	@Test
	void autorNaoPodeAnotarOProprioPostENotaDuplicadaERecusada() throws Exception {
		var autor = auth.authenticate("note_dono_" + System.nanoTime());
		var comunidade = auth.authenticate("note_dup_" + System.nanoTime());
		String postId = publicar(autor.token(), "note-pub-2");

		mockMvc.perform(post("/feed/posts/" + postId + "/notes")
				.header("X-Session-Token", autor.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"body", "Eu mesmo estou corrigindo meu post aqui."))))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("NOTE_ON_OWN_POST"));

		enviarNota(comunidade.token(), postId,
			"Faltou citar que o dado é de 2024, não de 2023.", "note-create-3");

		mockMvc.perform(post("/feed/posts/" + postId + "/notes")
				.header("X-Session-Token", comunidade.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"body", "Tentando mandar outra nota no mesmo post."))))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("NOTE_ALREADY_EXISTS"));

		var naoModerador = auth.authenticate("note_nao_mod_" + System.nanoTime());
		mockMvc.perform(get("/admin/notes").header("X-Session-Token", naoModerador.token()))
			.andExpect(status().isForbidden());
	}
}