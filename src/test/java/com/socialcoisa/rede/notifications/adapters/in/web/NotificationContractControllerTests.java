package com.socialcoisa.rede.notifications.adapters.in.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;
import com.socialcoisa.rede.shared.web.TestAuthHelper;

@SpringBootTest
@AutoConfigureMockMvc
class NotificationContractControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private org.springframework.jdbc.core.JdbcTemplate jdbc;

	private TestAuthHelper auth;

	@BeforeEach
	void setUp() {
		auth = new TestAuthHelper(mockMvc, objectMapper);
	}

	@Test
	void listsNotificationsAndMarksAllRead() throws Exception {
		var session = auth.authenticate("notif_user");

		mockMvc.perform(get("/notifications")
				.header("X-Session-Token", session.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items").isArray());

		mockMvc.perform(post("/notifications/read-all")
				.header("X-Session-Token", session.token())
				.header("Idempotency-Key", "notif-read-all-1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ALL_READ"));
	}

	@Test
	void marksASingleNotificationReadWithoutError() throws Exception {
		var session = auth.authenticate("notif_read_one");
		jdbc.update("""
				insert into notifications (id, user_id, kind, actor_id, title, body, `read`, created_at)
				values (?, ?, 'SYSTEM', 'sistema', 'Aviso', 'corpo', false, current_timestamp)
				""", "notif_read_id_1", session.userId());

		mockMvc.perform(post("/notifications/notif_read_id_1/read")
				.header("X-Session-Token", session.token())
				.header("Idempotency-Key", "notif-read-one"))
			.andExpect(status().isOk());

		mockMvc.perform(get("/notifications")
				.header("X-Session-Token", session.token()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[?(@.id == 'notif_read_id_1')].read").value(true));
	}
}
