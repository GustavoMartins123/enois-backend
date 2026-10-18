package com.socialcoisa.rede.events.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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

import tools.jackson.databind.ObjectMapper;
import com.socialcoisa.rede.events.domain.Event;
import com.socialcoisa.rede.shared.web.TestAuthHelper;

/**
 * A data do evento: o que é agendável e o que é engano.
 *
 * O caso que originou isto: alguém criou um evento em **220122-12-12**. O campo é texto livre e o
 * banco aceitava qualquer coisa — o evento entrava como válido, ficava no topo da lista para sempre
 * (a ordem é por data) e a faxina nunca o alcançava, porque para o sistema ele "ainda não aconteceu".
 *
 * O que este teste tranca:
 *
 * - data absurda é RECUSADA na criação (com o formato do app e o brasileiro);
 * - data ilegível também (o que não é data não vira evento);
 * - data de verdade passa, nos dois formatos;
 * - a data absurda que já está no banco conta como VENCIDA (é assim que a faxina limpa o que
 *   sobrou, sem ninguém precisar mexer no banco);
 * - só o dono (ou um admin) apaga o evento.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EventScheduleTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	private TestAuthHelper auth;

	@org.springframework.test.context.DynamicPropertySource
	static void adminAllowlist(org.springframework.test.context.DynamicPropertyRegistry registry) {
		registry.add("rede.admin.emails", () -> "evsched_admin@test.com");
	}

	@BeforeEach
	void setUp() {
		auth = new TestAuthHelper(mockMvc, objectMapper);
	}

	@Test
	void absurdDatesAreRejected() throws Exception {
		var creator = auth.authenticate("evsched_creator1");

		for (String date : List.of("220122-12-12", "1500-01-01", "12/12/220122", "31/02/2030", "ontem", "")) {
			mockMvc.perform(post("/events")
					.header("X-Session-Token", creator.token())
					.header("Idempotency-Key", "evsched-bad-" + Math.abs(date.hashCode()))
					.contentType(MediaType.APPLICATION_JSON)
					.content(body("Evento estranho", date)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_EVENT_DATE"));
		}
	}

	@Test
	void realisticDatesPassInBothFormats() throws Exception {
		var creator = auth.authenticate("evsched_creator2");

		create(creator, "Encontro do time", "2027-03-15", "evsched-ok-1").andExpect(status().isCreated());
		create(creator, "Encontro do time", "15/03/2027", "evsched-ok-2").andExpect(status().isCreated());
	}

	@Test
	void absurdDateAlreadyInTheDatabaseCountsAsExpired() {
		Instant now = Instant.parse("2026-10-18T12:00:00Z");
		Event absurdo = Event.create("event_x", "Evento impossivel", "220122-12-12", "10:59", "Presencial",
			"Alguem", null, null, null, "user_1", now);
		Event normal = Event.create("event_y", "Evento de verdade", "2027-03-15", "19:00", "Presencial",
			"Alguem", null, null, null, "user_1", now);

		assertThat(absurdo.hasAbsurdSchedule(now)).isTrue();
		assertThat(absurdo.isExpired(now, java.time.Duration.ZERO)).isTrue();
		assertThat(normal.hasAbsurdSchedule(now)).isFalse();
		assertThat(normal.isExpired(now, java.time.Duration.ZERO)).isFalse();
	}

	@Test
	void onlyTheOwnerOrAnAdminDeletes() throws Exception {
		var creator = auth.authenticate("evsched_creator3");
		var other = auth.authenticate("evsched_other3");
		var admin = auth.authenticate("evsched_admin");

		String eventId = objectMapper
			.readTree(create(creator, "Evento para apagar", "2027-05-20", "evsched-del").andReturn()
				.getResponse().getContentAsString())
			.get("id").asText();

		// Quem não é dono nem admin não apaga.
		mockMvc.perform(delete("/events/" + eventId).header("X-Session-Token", other.token())
				.header("Idempotency-Key", "evsched-del-403"))
			.andExpect(status().isForbidden());

		// O admin apaga (é assim que um evento absurdo sai do ar sem depender de banco).
		mockMvc.perform(delete("/events/" + eventId).header("X-Session-Token", admin.token())
				.header("Idempotency-Key", "evsched-del-admin"))
			.andExpect(status().isOk());

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.get("/events/" + eventId))
			.andExpect(status().isNotFound());
	}

	private org.springframework.test.web.servlet.ResultActions create(TestAuthHelper.AuthSession creator, String title,
			String date, String key) throws Exception {
		return mockMvc.perform(post("/events")
			.header("X-Session-Token", creator.token())
			.header("Idempotency-Key", key)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body(title, date)));
	}

	private String body(String title, String date) throws Exception {
		return objectMapper.writeValueAsString(Map.of("title", title, "date", date, "time", "19:00",
			"format", "Presencial", "host", "Énóis"));
	}
}
