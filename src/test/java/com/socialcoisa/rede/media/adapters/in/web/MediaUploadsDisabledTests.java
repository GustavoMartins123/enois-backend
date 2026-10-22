package com.socialcoisa.rede.media.adapters.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.socialcoisa.rede.shared.web.TestAuthHelper;

import tools.jackson.databind.ObjectMapper;

/**
 * URGENTE (19/10/2026): o ImageKit foi comprometido e nao recebe mais arquivo novo. Sem o S3
 * configurado, o upload responde 503 — nunca cai no caminho de escrita antigo.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MediaUploadsDisabledTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void uploadWithoutS3IsRefused() throws Exception {
		var user = new TestAuthHelper(mockMvc, objectMapper).authenticate("media_disabled_user");

		mockMvc.perform(post("/media/uploads")
				.header("X-Session-Token", user.token())
				.header("Idempotency-Key", "media-disabled-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"fileName", "media/x/foto.png", "mimeType", "image/png", "size", 1234,
					"purpose", "POST_IMAGE"))))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("UPLOADS_DISABLED"));
	}
}
