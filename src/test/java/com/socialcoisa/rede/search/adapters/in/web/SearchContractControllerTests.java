package com.socialcoisa.rede.search.adapters.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.socialcoisa.rede.shared.web.TestAuthHelper;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class SearchContractControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void searchesByKeyword() throws Exception {
		mockMvc.perform(get("/search")
				.param("q", "design")
				.param("types", "users,communities"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items").isArray());
	}

	@Test
	void returnsEmptyForGibberish() throws Exception {
		mockMvc.perform(get("/search")
				.param("q", "zzzzzznotfound")
				.param("types", "users"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items").isEmpty());
	}

	@Test
	void wildcardCharactersMatchLiterally() throws Exception {
		new TestAuthHelper(mockMvc, objectMapper).authenticate("likewildcard");
		for (String wildcard : new String[] { "%", "_", "!" }) {
			mockMvc.perform(get("/search")
					.param("q", wildcard)
					.param("types", "users,communities,jobs,events,mentors"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items").isEmpty());
		}
		mockMvc.perform(get("/search").param("q", "likewild").param("types", "users"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].title").value("likewildcard"));
	}
}
