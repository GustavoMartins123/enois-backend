package com.socialcoisa.rede.events.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.socialcoisa.rede.events.application.EventService;
import com.socialcoisa.rede.events.domain.Event;
import com.socialcoisa.rede.shared.web.TestAuthHelper;
import com.socialcoisa.rede.shared.web.TestDatabase;

import tools.jackson.databind.ObjectMapper;

/**
 * Eventos que passaram: saem da vitrine, nao aceitam mais presenca e, depois da folga, sao
 * sanitizados — as presencas (dado pessoal) somem e fica so o total.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EventSanitizationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private EventService eventService;

	private TestAuthHelper auth;

	@BeforeEach
	void setUp() {
		auth = new TestAuthHelper(mockMvc, objectMapper);
	}

	@Test
	void expiresAtComesFromTheEventDate() {
		Event iso = Event.create("e1", "Meetup", LocalDate.now().minusDays(2).toString(), "19:30", "ONLINE",
			"Dona", null, null, null, "user", Instant.now());
		assertThat(iso.expiresAt()).isPresent();
		assertThat(iso.isExpired(Instant.now(), Duration.ZERO)).isTrue();

		Event brazilian = Event.create("e2", "Meetup", "01/01/2020", null, "ONLINE", "Dona", null, null, null, "user",
			Instant.now());
		assertThat(brazilian.isExpired(Instant.now(), Duration.ZERO)).isTrue();

		// Sem data utilizavel nao ha vencimento: nunca sanitiza por palpite.
		Event undated = Event.create("e3", "Meetup", "amanha", "", "ONLINE", "Dona", null, null, null, "user", Instant.now());
		assertThat(undated.expiresAt()).isEmpty();
		assertThat(undated.isExpired(Instant.now(), Duration.ZERO)).isFalse();
	}

	@Test
	void pastEventsLeaveTheListingAndRefuseNewAttendance() throws Exception {
		var host = auth.authenticate("evt_host");
		String pastId = createEvent(host, "Evento que ja passou", LocalDate.now().minusDays(3).toString());
		String futureId = createEvent(host, "Evento futuro", LocalDate.now().plusDays(5).toString());

		// Vitrine so com o futuro.
		MvcResult listing = mockMvc.perform(get("/events").param("limit", "50"))
			.andExpect(status().isOk())
			.andReturn();
		String body = listing.getResponse().getContentAsString();
		assertThat(body).contains(futureId).doesNotContain(pastId);

		// O historico continua acessivel (marcado) quando pedido de proposito.
		mockMvc.perform(get("/events").param("limit", "50").param("includePast", "true"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[?(@.id == '" + pastId + "')].expired").value(true));

		// E nao aceita mais presenca.
		mockMvc.perform(post("/events/" + pastId + "/attendees")
				.header("X-Session-Token", host.token())
				.header("Idempotency-Key", "evt-past-attend"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("EVENT_FINISHED"));

		// O evento futuro aceita normalmente.
		mockMvc.perform(post("/events/" + futureId + "/attendees")
				.header("X-Session-Token", host.token())
				.header("Idempotency-Key", "evt-future-attend"))
			.andExpect(status().isOk());
	}

	@Test
	void sanitizationDropsAttendanceDataAndKeepsTheTotal() throws Exception {
		var host = auth.authenticate("evt_sanitize_host");
		var guest = auth.authenticate("evt_sanitize_guest");

		// Evento futuro, com presenca confirmada...
		String eventId = createEvent(host, "Evento para sanitizar", LocalDate.now().plusDays(2).toString());
		mockMvc.perform(post("/events/" + eventId + "/attendees")
				.header("X-Session-Token", guest.token())
				.header("Idempotency-Key", "evt-sanitize-attend"))
			.andExpect(status().isOk());
		assertThat(TestDatabase.countEventAttendees(eventId)).isEqualTo(1);

		// ...que terminou hoje de madrugada: ja saiu da vitrine, mas as 24h de folga ainda protegem
		// os dados. (A varredura cuida de varios eventos no mesmo banco, entao a asserta e sobre ESTE.)
		TestDatabase.setEventDate(eventId, LocalDate.now().toString(), "00:01");
		eventService.sanitizeExpired();
		assertThat(TestDatabase.countEventAttendees(eventId)).isEqualTo(1);

		// Passada a folga, a sanitizacao apaga o vinculo com cada pessoa.
		TestDatabase.setEventDate(eventId, LocalDate.now().minusDays(3).toString(), "10:00");
		EventService.SanitizationResult first = eventService.sanitizeExpired();
		assertThat(first.sanitized()).isGreaterThanOrEqualTo(1);
		assertThat(TestDatabase.countEventAttendees(eventId)).isZero();

		// O total sobrevive sem as identidades.
		mockMvc.perform(get("/events/" + eventId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.attendees").value(1))
			.andExpect(jsonPath("$.expired").value(true));

		// Idempotente: rodar de novo nao mexe mais neste evento (total e presencas seguem iguais).
		eventService.sanitizeExpired();
		assertThat(TestDatabase.countEventAttendees(eventId)).isZero();
		mockMvc.perform(get("/events/" + eventId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.attendees").value(1));

		// E depois da retencao o evento sai de vez.
		TestDatabase.setEventSanitizedAt(eventId, Instant.now().minus(Duration.ofDays(400)));
		assertThat(eventService.sanitizeExpired().purged()).isGreaterThanOrEqualTo(1);
		mockMvc.perform(get("/events/" + eventId)).andExpect(status().isNotFound());
		assertThat(TestDatabase.countEventAttendees(eventId)).isZero();
	}

	private String createEvent(TestAuthHelper.AuthSession session, String title, String date) throws Exception {
		MvcResult result = mockMvc.perform(post("/events")
				.header("X-Session-Token", session.token())
				.header("Idempotency-Key", "evt-" + title)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"title", title, "date", date, "time", "19:00", "format", "ONLINE", "host", "Casa"))))
			.andExpect(status().isCreated())
			.andReturn();
		return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
	}
}
