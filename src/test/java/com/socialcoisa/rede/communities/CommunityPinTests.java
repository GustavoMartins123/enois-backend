package com.socialcoisa.rede.communities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.MvcResult;

import com.socialcoisa.rede.shared.web.TestAuthHelper;

import tools.jackson.databind.ObjectMapper;

/**
 * Post fixado no topo da comunidade: um só, só ADMIN fixa, o post precisa ser DAQUELA comunidade,
 * e desafixar libera o topo.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CommunityPinTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	private TestAuthHelper authHelper() {
		return new TestAuthHelper(mockMvc, objectMapper);
	}

	private String createCommunity(TestAuthHelper.AuthSession dono, String name, String key) throws Exception {
		MvcResult result = mockMvc.perform(post("/communities")
				.header("X-Session-Token", dono.token())
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("name", name))))
			.andExpect(status().isCreated())
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
	}

	private String publish(TestAuthHelper.AuthSession autor, String communityId, String text, String key)
			throws Exception {
		MvcResult result = mockMvc.perform(post("/feed/posts")
				.header("X-Session-Token", autor.token())
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("type", "INSIGHT", "content", text,
					"communityId", communityId))))
			.andExpect(status().isCreated())
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder pin(
			TestAuthHelper.AuthSession quem, String communityId, String postId, String key) {
		return post("/communities/" + communityId + "/pin")
			.header("X-Session-Token", quem.token())
			.header("Idempotency-Key", key)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"postId\":\"" + postId + "\"}");
	}

	@Test
	void fixaUmSoESoAdmin() throws Exception {
		var dona = authHelper().authenticate("cpin-dona");
		var membro = authHelper().authenticate("cpin-membro");
		String communityId = createCommunity(dona, "Comunidade Pin", "cpin-create");
		String postA = publish(dona, communityId, "primeiro", "cpin-post-a");
		String postB = publish(dona, communityId, "segundo", "cpin-post-b");

		mockMvc.perform(post("/communities/" + communityId + "/members")
				.header("X-Session-Token", membro.token())
				.header("Idempotency-Key", "cpin-join"))
			.andExpect(status().isOk());

		// Membro comum não fixa.
		mockMvc.perform(pin(membro, communityId, postA, "cpin-member-pin"))
			.andExpect(status().isForbidden());

		// A dona fixa A.
		mockMvc.perform(pin(dona, communityId, postA, "cpin-pin-a"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(postA));

		mockMvc.perform(get("/communities/" + communityId + "/pin").header("X-Session-Token", dona.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(postA));

		// Fixar B substitui A.
		mockMvc.perform(pin(dona, communityId, postB, "cpin-pin-b"))
			.andExpect(status().isOk());

		mockMvc.perform(get("/communities/" + communityId + "/pin").header("X-Session-Token", dona.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(postB));

		// Post de OUTRA comunidade não pode ser fixado aqui.
		String outra = createCommunity(dona, "Outra Comunidade Pin", "cpin-create-2");
		String postDeOutra = publish(dona, outra, "de outra", "cpin-post-outra");
		mockMvc.perform(pin(dona, communityId, postDeOutra, "cpin-pin-outra"))
			.andExpect(status().isNotFound());

		// Desafixar libera o topo.
		mockMvc.perform(delete("/communities/" + communityId + "/pin")
				.header("X-Session-Token", dona.token())
				.header("Idempotency-Key", "cpin-unpin"))
			.andExpect(status().isOk());

		mockMvc.perform(get("/communities/" + communityId + "/pin").header("X-Session-Token", dona.token()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NO_PINNED_POST"));

		assertThat(postB).isNotEqualTo(postA);
	}
}
