package com.socialcoisa.rede.media.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

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
 * Midia de post e de perfil precisa ser legivel SEM sessao.
 *
 * Posts e perfis sao publicos (link compartilhado abre para qualquer um), e a grade de midia do
 * front busca cada asset por id: se essa leitura exigir sessao, o post aparece sem imagem — foi o
 * que aconteceu com "post com midia nao mostra a midia". O id e opaco (uuid) e so aparece em
 * conteudo que ja esta publico, entao a leitura nao expoe nada novo.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MediaPublicReadTests {

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
	void anyoneCanReadMediaPublishedInAPost() throws Exception {
		var session = auth.authenticate("user_media_public_read");
		MvcResult created = mockMvc.perform(post("/media/uploads")
				.header("X-Session-Token", session.token())
				.header("Idempotency-Key", "media-public-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("fileName", "foto.png", "mimeType", "image/png",
					"size", 4096, "purpose", "POST_IMAGE"))))
			.andExpect(status().isCreated())
			.andReturn();
		String mediaId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

		// Visitante sem sessao: e o caso do link compartilhado e do post no feed publico.
		mockMvc.perform(get("/media/" + mediaId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(mediaId))
			.andExpect(jsonPath("$.mimeType").value("image/png"))
			.andExpect(jsonPath("$.cdnUrl").isNotEmpty());
	}

	@Test
	void uploadAndDeleteStillRequireASession() throws Exception {
		var session = auth.authenticate("user_media_public_write");
		MvcResult created = mockMvc.perform(post("/media/uploads")
				.header("X-Session-Token", session.token())
				.header("Idempotency-Key", "media-public-2")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("fileName", "doc.pdf", "mimeType", "application/pdf",
					"size", 1024, "purpose", "POST_FILE"))))
			.andExpect(status().isCreated())
			.andReturn();
		String mediaId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

		mockMvc.perform(post("/media/uploads")
				.header("Idempotency-Key", "media-public-3")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("fileName", "x.png", "mimeType", "image/png", "size", 10,
					"purpose", "POST_IMAGE"))))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(delete("/media/" + mediaId).header("Idempotency-Key", "media-public-4"))
			.andExpect(status().isUnauthorized());
	}
}
