package com.socialcoisa.rede.mentoring.adapters.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.socialcoisa.rede.shared.web.TestAuthHelper;

import tools.jackson.databind.ObjectMapper;

/** Banner do perfil de mentor e agenda: criar/remover horário pelo dono. */
@SpringBootTest
@AutoConfigureMockMvc
class MentorProfileTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void mentorHasBannerAndCanManageAgenda() throws Exception {
		var mentor = new TestAuthHelper(mockMvc, objectMapper).authenticate("mentor_profile_owner");

		MvcResult created = mockMvc.perform(post("/mentors")
				.header("X-Session-Token", mentor.token())
				.header("Idempotency-Key", "mentor-banner-1")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"expertise", List.of("Java", "Spring"),
					"price", "Gratuito",
					"banner", "https://cdn.enois.test/banner.jpg"))))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.banner").value("https://cdn.enois.test/banner.jpg"))
			.andReturn();
		String mentorId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

		String startsAt = Instant.now().plusSeconds(86_400).toString();
		String endsAt = Instant.now().plusSeconds(90_000).toString();
		MvcResult slot = mockMvc.perform(post("/mentors/" + mentorId + "/slots")
				.header("X-Session-Token", mentor.token())
				.header("Idempotency-Key", "mentor-slot-" + UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("startsAt", startsAt, "endsAt", endsAt))))
			.andExpect(status().isCreated())
			.andReturn();
		String slotId = objectMapper.readTree(slot.getResponse().getContentAsString()).get("id").asText();

		mockMvc.perform(get("/mentors/" + mentorId + "/slots"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[?(@.id == '" + slotId + "')]").exists());

		mockMvc.perform(delete("/mentors/" + mentorId + "/slots/" + slotId)
				.header("X-Session-Token", mentor.token()))
			.andExpect(status().isNoContent());

		mockMvc.perform(get("/mentors/" + mentorId + "/slots"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(0));
	}
}
