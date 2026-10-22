package com.socialcoisa.rede.profile.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.socialcoisa.rede.shared.web.TestAuthHelper;

/**
 * Currículo do perfil: o PDF que a pessoa sobe para mandar nas candidaturas.
 *
 * O que este teste protege, além do vai-e-volta: **o currículo NÃO sai na projeção pública**. Ele tem
 * telefone, endereço e histórico — é dado que a pessoa entrega quando quer (no envio da candidatura),
 * não na página que qualquer um abre. Se alguém um dia "simplificar" isso colocando o campo na
 * `UserProfileView`, é aqui que quebra.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProfileResumeTests {

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
	void savesTheResumeAndReadsItBackOnlyForTheOwner() throws Exception {
		var user = auth.authenticate("user_resume1");
		String url = "https://ik.imagekit.io/x/curriculo-ana.pdf";

		mockMvc.perform(patch("/me/profile")
				.header("X-Session-Token", user.token())
				.header("Idempotency-Key", "resume-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("resumeUrl", url, "resumeName", "curriculo-ana.pdf"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.resumeUrl").value(url))
			.andExpect(jsonPath("$.resumeName").value("curriculo-ana.pdf"));

		MvcResult me = mockMvc.perform(get("/me").header("X-Session-Token", user.token()))
			.andExpect(status().isOk())
			.andReturn();
		JsonNode mine = objectMapper.readTree(me.getResponse().getContentAsString());
		assertThat(mine.get("resumeUrl").asText()).isEqualTo(url);

		// Publico: o perfil de terceiro NAO mostra o curriculo (nem o endereco, nem o nome).
		mockMvc.perform(get("/users/" + mine.get("username").asText()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.resumeUrl").doesNotExist())
			.andExpect(jsonPath("$.resumeName").doesNotExist());
	}

	@Test
	void keepsTheResumeWhenTheFieldIsAbsentAndClearsWithAnEmptyString() throws Exception {
		var user = auth.authenticate("user_resume2");

		mockMvc.perform(patch("/me/profile")
				.header("X-Session-Token", user.token())
				.header("Idempotency-Key", "resume-2a")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("resumeUrl", "https://ik.imagekit.io/x/cv.pdf",
					"resumeName", "cv.pdf"))))
			.andExpect(status().isOk());

		// Editar outro campo nao pode derrubar o curriculo (campo ausente mantem).
		mockMvc.perform(patch("/me/profile")
				.header("X-Session-Token", user.token())
				.header("Idempotency-Key", "resume-2b")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("headline", "Back-end"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.resumeUrl").value("https://ik.imagekit.io/x/cv.pdf"));

		// String vazia limpa os dois campos.
		mockMvc.perform(patch("/me/profile")
				.header("X-Session-Token", user.token())
				.header("Idempotency-Key", "resume-2c")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("resumeUrl", "", "resumeName", ""))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.resumeUrl").doesNotExist())
			.andExpect(jsonPath("$.resumeName").doesNotExist());
	}

	@Test
	void refusesAResumeUrlAboveTheColumnLimit() throws Exception {
		var user = auth.authenticate("user_resume3");

		mockMvc.perform(patch("/me/profile")
				.header("X-Session-Token", user.token())
				.header("Idempotency-Key", "resume-3")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("resumeUrl", "a".repeat(501)))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("FIELD_TOO_LONG"));
	}
}
