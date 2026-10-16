package com.socialcoisa.rede.profile.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.socialcoisa.rede.shared.web.TestAuthHelper;

/**
 * Campo "Habilidades": 800 caracteres, o mesmo teto do campo no front.
 *
 * Antes eram 120 por item, o que recusava quem escreve as habilidades como uma frase em vez de
 * tags soltas — o campo deixava digitar e o servidor recusava no fim.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProfileSkillsLengthTests {

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
	void acceptsASkillWithUpToEightHundredCharacters() throws Exception {
		var user = auth.authenticate("user_skills_length1");
		String longSkill = "a".repeat(800);

		mockMvc.perform(patch("/me/profile")
				.header("X-Session-Token", user.token())
				.header("Idempotency-Key", "skills-800")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("skills", List.of(longSkill)))))
			.andExpect(status().isOk());

		MvcResult read = mockMvc.perform(get("/me").header("X-Session-Token", user.token()))
			.andExpect(status().isOk())
			.andReturn();
		JsonNode skills = objectMapper.readTree(read.getResponse().getContentAsString()).get("skills");
		assertThat(skills.get(0).asText()).hasSize(800);
	}

	@Test
	void rejectsAboveTheLimitWithAFieldMessage() throws Exception {
		var user = auth.authenticate("user_skills_length2");

		mockMvc.perform(patch("/me/profile")
				.header("X-Session-Token", user.token())
				.header("Idempotency-Key", "skills-801")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("skills", List.of("a".repeat(801))))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("FIELD_TOO_LONG"))
			.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("800")));
	}
}
