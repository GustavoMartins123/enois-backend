package com.socialcoisa.rede.apiv1.origins;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.socialcoisa.rede.shared.web.TestAuthHelper;

import tools.jackson.databind.ObjectMapper;

/** A API pública só recebe CORS de uma origem cadastrada; as demais são recusadas. */
@SpringBootTest
@AutoConfigureMockMvc
class DeveloperOriginTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void registeredOriginGetsCorsAndUnknownOriginsAreBlocked() throws Exception {
		var user = new TestAuthHelper(mockMvc, objectMapper).authenticate("origin_user");

		mockMvc.perform(post("/developers/origins")
				.header("X-Session-Token", user.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"origin\":\"http://app.example\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_ORIGIN"));

		MvcResult created = mockMvc.perform(post("/developers/origins")
				.header("X-Session-Token", user.token())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"origin\":\"https://app.example/\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.origin").value("https://app.example"))
			.andReturn();
		String id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asString();

		mockMvc.perform(options("/v1")
				.header("Origin", "https://app.example")
				.header("Access-Control-Request-Method", "GET")
				.header("Access-Control-Request-Headers", "X-Session-Token"))
			.andExpect(status().isNoContent())
			.andExpect(header().string("Access-Control-Allow-Origin", "https://app.example"));

		mockMvc.perform(options("/v1")
				.header("Origin", "https://evil.example")
				.header("Access-Control-Request-Method", "GET"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ORIGIN_NOT_ALLOWED"));

		// Sem Origin (servidor-a-servidor) continua funcionando.
		mockMvc.perform(get("/v1")).andExpect(status().isOk());

		mockMvc.perform(delete("/developers/origins/" + id).header("X-Session-Token", user.token()))
			.andExpect(status().isOk());
	}
}
